package com.venn.velocity.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.venn.velocity.model.LoadAttempt;
import com.venn.velocity.model.LoadResponse;
import com.venn.velocity.service.VelocityLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Optional;

@Component
public class FileProcessorRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(FileProcessorRunner.class);

    private final VelocityLimitService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.input.file:input.txt}")
    private String inputFile;

    @Value("${app.output.file:}")
    private String outputFile;

    public FileProcessorRunner(VelocityLimitService service) {
        this.service = service;
    }

    @Override
    public void run(String... args) throws Exception {
        // Skip processing if no input file is configured (e.g. during tests)
        if (inputFile.isBlank()) {
            log.info("No input file configured, skipping processing");
            return;
        }

        log.info("Processing input file: {}", inputFile);

        // Open output file writer if an output path is configured, otherwise stdout only
        PrintWriter fileWriter = outputFile.isBlank() ? null : new PrintWriter(new FileWriter(outputFile));
        int total = 0, accepted = 0, rejected = 0, skipped = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip blank lines
                if (line.isEmpty()) continue;

                try {
                    // Parse each line as a LoadAttempt JSON object
                    LoadAttempt attempt = objectMapper.readValue(line, LoadAttempt.class);
                    Optional<LoadResponse> response = service.ValidateAndExecute(attempt);

                    if (response.isPresent()) {
                        total++;
                        String json = objectMapper.writeValueAsString(response.get());
                        // Always print to stdout; also write to file if configured
                        System.out.println(json);
                        if (fileWriter != null) {
                            fileWriter.println(json);
                        }
                        if (response.get().isAccepted()) accepted++; else rejected++;
                    } else {
                        // Duplicate load ID case: no response emitted per spec
                        skipped++;
                    }
                } catch (Exception e) {
                    // Log and continue so one bad line
                    log.error("Failed to process line: {}", line, e);
                }
            }
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }

        log.info("Done — total={} accepted={} rejected={} duplicates_skipped={}", total, accepted, rejected, skipped);
        if (!outputFile.isBlank()) {
            log.info("Output written to: {}", outputFile);
        }
    }
}
