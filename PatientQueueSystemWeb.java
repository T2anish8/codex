import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

public class PatientQueueSystemWeb {

    static class Patient {
        String id;
        String name;
        int age;
        String gender;

        Patient(String name, int age, String gender) {
            this.id = UUID.randomUUID().toString();
            this.name = name;
            this.age = age;
            this.gender = gender;
        }
    }

    private static final Queue<Patient> queue = new LinkedList<>();
    private static final int AVG_TIME_PER_PATIENT = 5; // minutes

    public static void main(String[] args) throws IOException {
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", exchange -> {
            String method = exchange.getRequestMethod();

            if ("POST".equalsIgnoreCase(method)) {
                InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
                BufferedReader br = new BufferedReader(isr);
                String formData = br.readLine();

                Map<String, String> params = parseFormData(formData);
                String name = params.get("name");
                int age = Integer.parseInt(params.get("age"));
                String gender = params.get("gender");

                Patient newPatient = new Patient(name, age, gender);
                synchronized (queue) {
                    queue.add(newPatient);
                }

                // Redirect to status page
                exchange.getResponseHeaders().set("Location", "/status?id=" + newPatient.id);
                exchange.sendResponseHeaders(303, -1);
                return;
            }

            // GET: Show form and waiting list
            String response = buildHomePage();
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });

        server.createContext("/status", exchange -> {
            Map<String, String> queryParams = parseQuery(exchange.getRequestURI().getQuery());
            String id = queryParams.get("id");
            String response = buildStatusPage(id);
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:" + port);
    }

    private static String buildHomePage() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Patient Queue</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', sans-serif; margin: 0; padding: 0; background-color: #f0f2f5; }");
        html.append(".container { max-width: 800px; margin: 40px auto; background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }");
        html.append("h1 { color: #2c3e50; }");
        html.append("form { margin-top: 20px; }");
        html.append("input, select, button { width: 100%; padding: 10px; margin-top: 10px; border-radius: 5px; border: 1px solid #ccc; font-size: 14px; }");
        html.append("button { background-color: #27ae60; color: white; border: none; cursor: pointer; transition: background 0.3s; }");
        html.append("button:hover { background-color: #219150; }");
        html.append(".list { margin-top: 40px; }");
        html.append("ol { padding-left: 20px; }");
        html.append("li { margin-bottom: 8px; }");
        html.append("</style></head><body>");
        html.append("<div class='container'>");
        html.append("<h1>Patient Queue System</h1>");

        html.append("<form method='post'>");
        html.append("<label>Name:</label><input type='text' name='name' required>");
        html.append("<label>Age:</label><input type='number' name='age' required>");
        html.append("<label>Gender:</label>");
        html.append("<select name='gender'><option>Male</option><option>Female</option><option>Other</option></select>");
        html.append("<button type='submit'>Add to Queue</button>");
        html.append("</form>");

        html.append("<div class='list'><h2>Current Waiting List</h2>");
        synchronized (queue) {
            if (queue.isEmpty()) {
                html.append("<p>No patients are currently in the queue.</p>");
            } else {
                html.append("<ol>");
                for (Patient p : queue) {
                    html.append("<li>").append(p.name).append(" (").append(p.age).append(", ").append(p.gender).append(")</li>");
                }
                html.append("</ol>");
                html.append("<p><strong>Estimated wait time for a new patient: ")
                        .append(queue.size() * AVG_TIME_PER_PATIENT)
                        .append(" minutes</strong></p>");
            }
        }
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String buildStatusPage(String id) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><title>Your Status</title>");
        html.append("<style>");
        html.append("body { font-family: 'Segoe UI', sans-serif; margin: 0; padding: 0; background-color: #f0f2f5; }");
        html.append(".container { max-width: 500px; margin: 60px auto; background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }");
        html.append("h1 { color: #2980b9; }");
        html.append("p { font-size: 16px; }");
        html.append("a { display: inline-block; margin-top: 20px; text-decoration: none; color: #3498db; }");
        html.append("</style></head><body>");
        html.append("<div class='container'>");
        html.append("<h1>Your Queue Status</h1>");

        synchronized (queue) {
            int position = 0;
            boolean found = false;
            for (Patient p : queue) {
                if (p.id.equals(id)) {
                    found = true;
                    break;
                }
                position++;
            }

            if (found) {
                int waitTime = position * AVG_TIME_PER_PATIENT;
                html.append("<p>There are <strong>").append(position).append("</strong> patients ahead of you.</p>");
                html.append("<p>Your estimated wait time is <strong>").append(waitTime).append(" minutes</strong>.</p>");
            } else {
                html.append("<p><strong>Invalid or expired patient ID.</strong></p>");
            }
        }

        html.append("<a href='/'>← Back to Main Page</a>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static Map<String, String> parseFormData(String formData) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        for (String pair : formData.split("&")) {
            String[] parts = pair.split("=");
            String key = java.net.URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : "";
            map.put(key, value);
        }
        return map;
    }

    private static Map<String, String> parseQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> map = new HashMap<>();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=");
            String key = java.net.URLDecoder.decode(parts[0], "UTF-8");
            String value = parts.length > 1 ? java.net.URLDecoder.decode(parts[1], "UTF-8") : "";
            map.put(key, value);
        }
        return map;
    }
}
