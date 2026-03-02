package io.sealsecurity.demo.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Simple welcome page. Takes a name, parses it through SnakeYAML, displays a greeting.
 * SnakeYAML 1.33 is vulnerable to CVE-2022-1471 (arbitrary object instantiation).
 */
@RestController
public class HelloController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return greeting("World");
    }

    @PostMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String submit(@RequestParam("name") String name) {
        return greeting(name);
    }

    private String greeting(String name) {
        Yaml yaml = new Yaml();
        Object parsed = yaml.load(name);
        String displayName = String.valueOf(parsed);

        return "<html>" +
            "<head>" +
            "<title>Welcome</title>" +
            "<link href=\"https://cdn.jsdelivr.net/npm/bootstrap@5.0.2/dist/css/bootstrap.min.css\" rel=\"stylesheet\">" +
            "</head>" +
            "<body style=\"background:#f8f9fa;\">" +
            "<div style=\"max-width:500px; margin:100px auto; text-align:center;\">" +
            "<h1 class=\"mb-4\">Welcome, " + escapeHtml(displayName) + "!</h1>" +
            "<form method=\"POST\" action=\"/\">" +
            "<div class=\"input-group mb-3\">" +
            "<input type=\"text\" name=\"name\" class=\"form-control\" placeholder=\"Enter your name\" value=\"\">" +
            "<button type=\"submit\" class=\"btn btn-primary\">Go</button>" +
            "</div>" +
            "</form>" +
            "</div>" +
            "</body>" +
            "</html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
