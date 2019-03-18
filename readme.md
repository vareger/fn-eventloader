# Event Loader
## Application fetches all transaction logs from blockchain and publishes to message broker (Kafka)

##### Build
`gradle build`

##### Configuration
| Property                                 | Type   | Environment         | Description                                                     |
|------------------------------------------|--------|---------------------|-----------------------------------------------------------------|
| spring.kafka.bootstrap-servers           | string | KAFKA_BOOTSTRAP_URL | Urls to Kafka bootstrap server                                  |
| spring.kafka.producer.value-serializer   | string | ---                 | Class of value serializer                                       |
| spring.kafka.producer.retries            | number | ---                 | Amount of trying to commit message                              |
| spring.kafka.producer.acks               | string | ---                 | Required amount of acknowledgments                              |
| spring.kafka.consumer.value-deserializer | string | ---                 | Class of value deserializer                                     |
| spring.kafka.consumer.group-id           | string | GROUP_ID            | Name of the event-loader group for Kafka                        |
| spring.kafka.client-id                   | string | CLIENT_ID           | Id of the Kafka client                                          |
| ethereum.client-address                  | string | NODE_URL            | Url to json-rpc web3                                            |
| ethereum.batch-size                      | number | BLOCK_BATCH_SIZE    | Amount of blocks to batch during single iteration (Default: 10) |
| ethereum.start-block                     | number | START_BLOCK         | Number of block from which fetching will start                  |
| ethereum.block-lag                       | number | BLOCK_LAG           | Amount of blocks from latest that won't process (Default: 12)   |
| zookeeper.namespace                      | string | ZOOKEEPER_NAMESPACE | Root path of the zookeeper node                                 |
| zookeeper.connect-string                 | string | ZOOKEEPER_URL       | Url to Zookeeper node                                           |
| zookeeper.connection-timeout             | number | ---                 | Timeout of connection to Zookeeper in ms (Default: 3000)        |
| zookeeper.session-timeout                | number | ---                 | Session timeout in ms (Default: 10000)                          |