package com.cdata.embeddeddrivers.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a public S3 bucket over plain HTTP: object listings, prefix
 * discovery, object fetches, and file downloads. Knows nothing about what
 * the bucket contains.
 */
class BucketReader {

    private static final Pattern PAT_CONTENTS      = Pattern.compile("<Contents>(.*?)</Contents>", Pattern.DOTALL);
    private static final Pattern PAT_KEY           = Pattern.compile("<Key>([^<]+)</Key>");
    private static final Pattern PAT_SIZE          = Pattern.compile("<Size>(\\d+)</Size>");
    private static final Pattern PAT_COMMON_PREFIX = Pattern.compile("<CommonPrefixes>\\s*<Prefix>([^<]+)</Prefix>");
    private static final Pattern PAT_CONTINUATION  = Pattern.compile("<NextContinuationToken>([^<]+)</NextContinuationToken>");

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    BucketReader(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Response body plus HTTP status, so callers can distinguish 404 from other failures. */
    record HttpResult(int status, String body) {
    }

    HttpResult get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    /** Fetches a single object's body by bucket key. */
    HttpResult getObject(String key) throws IOException {
        return get(baseUrl + "/" + key);
    }

    /** Lists all objects (with sizes) under a prefix, following pagination. */
    List<RemoteFile> listFiles(String prefix) throws IOException {
        List<RemoteFile> files = new ArrayList<>();
        forEachPage(prefix, null, body -> {
            Matcher cm = PAT_CONTENTS.matcher(body);
            while (cm.find()) {
                String block = cm.group(1);
                Matcher km = PAT_KEY.matcher(block);
                if (!km.find()) continue;
                Matcher sm = PAT_SIZE.matcher(block);
                long size = sm.find() ? Long.parseLong(sm.group(1)) : -1;
                files.add(new RemoteFile(xmlUnescape(km.group(1)), size));
            }
        });
        return files;
    }

    /** Lists the immediate child prefixes ("directories") under a prefix, following pagination. */
    List<String> listPrefixes(String prefix) throws IOException {
        List<String> prefixes = new ArrayList<>();
        forEachPage(prefix, "/", body -> {
            Matcher m = PAT_COMMON_PREFIX.matcher(body);
            while (m.find()) {
                prefixes.add(xmlUnescape(m.group(1)));
            }
        });
        return prefixes;
    }

    /**
     * Downloads a bucket object to {@code destDir}, keeping its filename.
     * Streams to a .part file and renames on success, so an interrupted
     * download never leaves a truncated file under the final name.
     * Creates the directory if needed. Returns the path written.
     */
    Path download(RemoteFile file, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(file.filename());
        Path part = destDir.resolve(file.filename() + ".part");
        String url = baseUrl + "/" + file.key();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        try {
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(part));
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
            }
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + url, e);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** Runs a ListObjectsV2 query, invoking {@code onPage} per page until pagination ends. */
    private void forEachPage(String prefix, String delimiter, PageConsumer onPage) throws IOException {
        String continuationToken = null;
        do {
            StringBuilder url = new StringBuilder(baseUrl)
                    .append("/?list-type=2&prefix=")
                    .append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
            if (delimiter != null) {
                url.append("&delimiter=").append(URLEncoder.encode(delimiter, StandardCharsets.UTF_8));
            }
            if (continuationToken != null) {
                url.append("&continuation-token=").append(URLEncoder.encode(continuationToken, StandardCharsets.UTF_8));
            }
            HttpResult res = get(url.toString());
            if (res.status() != 200) {
                throw new IOException("S3 list returned HTTP " + res.status() + " for prefix: " + prefix);
            }
            onPage.accept(res.body());
            if (res.body().contains("<IsTruncated>true</IsTruncated>")) {
                Matcher tm = PAT_CONTINUATION.matcher(res.body());
                continuationToken = tm.find() ? tm.group(1) : null;
            } else {
                continuationToken = null;
            }
        } while (continuationToken != null);
    }

    private interface PageConsumer {
        void accept(String pageBody);
    }

    /** Unescapes XML entities in S3 ListObjectsV2 responses. */
    private static String xmlUnescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&apos;", "'").replace("&quot;", "\"");
    }
}
