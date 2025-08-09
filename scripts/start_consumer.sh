# 运行 ./scripts/start_consumer.sh , timeout-ms 是 一共执行多少毫秒的。如果没有它，则会一直执行下去.
~/tools/kafka_2.13-3.7.2/bin/kafka-console-consumer.sh \
  --bootstrap-server b-2-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9198,b-1-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9198 \
  --topic user_behavior \
  --consumer.config ~/tools/kafka_2.13-3.7.2/client.properties \
  --from-beginning \
  --timeout-ms 15000