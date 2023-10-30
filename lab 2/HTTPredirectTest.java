import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;

// toggle HttpClient.Redirect.ALWAYS / HttpClient.Redirect.NEVER

public class HTTPredirectTest {

    public static void main(String[] args) throws IOException, InterruptedException {

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS).build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://httpbin.org/redirect/3"))
                .GET()
                .build();

                HttpResponse response = client.send(request,
                HttpResponse.BodyHandlers.discarding());

        System.out.println(response.statusCode());

        HttpHeaders headers = response.headers();

        headers.map().forEach((key, values) -> {
            System.out.printf("%s: %s%n", key, values);
        });
    }
}