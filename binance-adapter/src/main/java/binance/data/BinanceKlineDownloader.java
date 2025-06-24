package binance.data;import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BinanceKlineDownloader {

    private static final List<String> symbols = Arrays.asList("BTCEUR", "ETHEUR", "BNBEUR","ATOMEUR", "SOLEUR", "EURUSDC", "EURUSDT");

    private static final LocalDate startDate = LocalDate.of(2022, 1, 1);
    private static final LocalDate endDate = LocalDate.of(2024, 12, 31);

    private static final long ONE_DAY_MS = 24 * 60 * 60 * 1000L;
    private static final long MAX_INTERVAL = ONE_DAY_MS * 1000L; // max circa 3 anni

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    public static void main(String[] args) {
        long startTime = startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();
        long endTime = endDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli();

        for (String symbol : symbols) {
            try {
                System.out.println("Scarico: " + symbol);
                downloadKlines(symbol, "1d", startTime, endTime);
                System.out.println("✓ Completato: " + symbol + ".csv");
            } catch (Exception e) {
                System.err.println("✗ Errore per " + symbol + ": " + e.getMessage());
            }
        }
    }

    private static void downloadKlines(String symbol, String interval, long startTime, long endTime) throws IOException {
        String filename = "./input/"+symbol + ".csv.prices";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("Symbol,Date,Open,High,Low,Close,Volume\n");

            long currentStart = startTime;
            while (currentStart < endTime) {
                long currentEnd = Math.min(currentStart + MAX_INTERVAL, endTime);
                String urlStr = String.format(
                    "https://api.binance.com/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=1000",
                    symbol, interval, currentStart, currentEnd
                );

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() != 200) {
                    throw new IOException("Errore Binance API: HTTP " + conn.getResponseCode());
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String json = in.readLine();
                in.close();

                // Rimuove [ ] esterni
                json = json.substring(1, json.length() - 1);
                String[] entries = json.split("\\],\\[");

                for (String entry : entries) {
                    String clean = entry.replaceAll("[\\[\\]]", "");
                    String[] fields = clean.split(",");

                    if (fields.length >= 6) {
                        long timestamp = Long.parseLong(fields[0]);
                        String dateStr = formatter.format(Instant.ofEpochMilli(timestamp));
                        writer.write(String.join(",", symbol.replace("EUR", ""), dateStr, fields[1], fields[2], fields[3], fields[4], fields[5]));
                        writer.newLine();
                    }
                }

                currentStart = currentEnd + 1;
            }
        }
    }
}
