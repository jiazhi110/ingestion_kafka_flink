package com.myjustin.flink.config;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    public static Map<String, String> loadConfig() {
        // Fetch project context from environment variables
        String projectName = System.getenv("PROJECT_NAME");
        String environment = System.getenv("ENVIRONMENT");
        String awsRegion = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

        if (projectName == null || environment == null) {
            LOG.error("CRITICAL ERROR: Environment variables 'PROJECT_NAME' and 'ENVIRONMENT' are not set.");
            throw new RuntimeException("Missing mandatory configuration variables: PROJECT_NAME, ENVIRONMENT");
        }

        LOG.info("Loading Configuration. Project: {}, Environment: {}, Region: {}", projectName, environment, awsRegion);

        SsmClient ssmClient = SsmClient.builder()
                .region(Region.of(awsRegion))
                .build();

        Map<String, String> config = new HashMap<>();
        config.put("kafka.bootstrap.servers", getParameter(ssmClient, String.format("/%s/%s/kafka/bootstrap_brokers_sasl_iam", projectName, environment)));
        config.put("kafka.topic", getParameter(ssmClient, String.format("/%s/%s/kafka/topic_name", projectName, environment)));
        config.put("s3.output.bucket", getParameter(ssmClient, String.format("/%s/%s/s3/flink_output_bucket", projectName, environment)));
        config.put("s3.dlq.path", getParameter(ssmClient, String.format("/%s/%s/s3/flink_dlq_path", projectName, environment)));
        config.put("kafka.group.id", getParameter(ssmClient, String.format("/%s/%s/kafka/consumer_group_id", projectName, environment)));

        // Print loaded config (masked where appropriate)
        LOG.info("=========================================== Configuration Loaded ====================================================");
        config.forEach((k, v) -> LOG.info("{}: {}", k, v));
        LOG.info("=====================================================================================================================");

        return config;
    }

    private static String getParameter(SsmClient ssmClient, String parameterName) {
        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(true)
                    .build();
            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();
        } catch (Exception e) {
            LOG.error("Error getting parameter: {}. Error: {}", parameterName, e.getMessage());
            throw new RuntimeException("Failed to get parameter: " + parameterName, e);
        }
    }
}
