package io.sealsecurity.demo.service;

import org.apache.commons.text.StringSubstitutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

/**
 * Service demonstrating usage of vulnerable dependencies.
 * These dependencies are automatically patched by Seal Security via JFrog.
 */
@Service
public class DataService {

    private static final Logger logger = LogManager.getLogger(DataService.class);

    public String processTemplate(String template, Map<String, String> values) {
        StringSubstitutor substitutor = new StringSubstitutor(values);
        String result = substitutor.replace(template);
        logger.info("Processed template with Commons Text");
        return result;
    }

    public Map<String, Object> parseYaml(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(yamlContent);
        logger.info("Parsed YAML content");
        return data;
    }

    public void logActivity(String activity) {
        logger.info("Activity logged: {}", activity);
        logger.debug("Debug information for: {}", activity);
    }

    public String getDemoData() {
        Map<String, String> values = new HashMap<>();
        values.put("name", "Seal Security JFrog Demo");
        values.put("status", "Protected via JFrog");

        String template = "Application: ${name}, Status: ${status}";
        return processTemplate(template, values);
    }
}
