from confluent_kafka.admin import AdminClient, ACLBinding, AclOperation, AclPermissionType, ResourcePattern, ResourceType

conf = {
    'bootstrap.servers': 'b-1-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9196,b-2-public.flinkstagingkafkaclus.oj6v2z.c23.kafka.us-east-1.amazonaws.com:9196',
    'security.protocol': 'SASL_SSL',
    'sasl.mechanism': 'SCRAM-SHA-512',
    'sasl.username': 'flink-user',  # 改成你的用户名
    'sasl.password': 'jiazhi',     # 改成你的密码
    'ssl.ca.location': 'AmazonRootCA1.pem',  # 改成你的路径
}

admin_client = AdminClient(conf)

binding = ACLBinding(
    resource=ResourcePattern(ResourceType.TOPIC, "user_behavior", "LITERAL"),
    principal="User:flink-user",
    host="*",
    operation=AclOperation.ALL,  # 或者 WRITE / READ，最保险用 ALL
    permission_type=AclPermissionType.ALLOW
)

fs = admin_client.create_acls([binding])
for future in fs.values():
    try:
        future.result()
        print("✅ 权限添加成功！你现在可以创建 topic 和生产消息了。")
    except Exception as e:
        print(f"❌ 添加 ACL 失败：{e}")
