package util

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.model.PurgeQueueRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageBatchRequest
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry
import com.amazonaws.services.sqs.model.SendMessageRequest

object SQSUtil {
  private val sqsClient: AmazonSQS = AmazonSQSClientBuilder.defaultClient()

  /**
   * SQSにメッセージを送信（FIFOキューではグループIDなどが必要になるため使用できない）
   *
   * @param queueUrlVal
   * @param message
   */
  suspend fun sendMessages(queueUrlVal: String, message: String) {
    val sendRequest = SendMessageRequest()
    sendRequest.run {
      withQueueUrl(queueUrlVal)
      withMessageBody(message)
    }

    try {
      sqsClient.sendMessage(sendRequest)
    } catch (ex: Exception) {
      println(ex.message)
      throw RuntimeException()
    }
  }

  suspend fun sendBatchMessages(queueUrlVal: String, messages: List<SendMessageBatchRequestEntry>) {
    val sendMessageBatchRequest = SendMessageBatchRequest()
    sendMessageBatchRequest.run {
      withQueueUrl(queueUrlVal)
      withEntries(messages)
    }

    try {
      sqsClient.sendMessageBatch(sendMessageBatchRequest)
    } catch (ex: Exception) {
      println(ex.message)
      throw RuntimeException()
    }
  }

  /**
   * メッセージを一括で送信
   *
   * @param queueUrlVal
   * @param maxNumberOfMessages
   * @return
   */
  suspend fun receiveMessages(queueUrlVal: String, maxNumberOfMessages: Int): List<Message> {
    val receiveMessageRequest = ReceiveMessageRequest()
    receiveMessageRequest.run {
      withQueueUrl(queueUrlVal)
      withMaxNumberOfMessages(maxNumberOfMessages)
    }

    return try {
      sqsClient.receiveMessage(receiveMessageRequest).messages
    } catch (ex: Exception) {
      throw RuntimeException()
    }
  }

  /**
   * キューから対象のメッセージを削除
   *
   * @param queueUrlVal
   * @param receiptHandle
   */
  suspend fun deleteMessage(queueUrlVal: String, receiptHandle: String) {
    val deleteMessageRequest = DeleteMessageRequest()
    deleteMessageRequest.run {
      withQueueUrl(queueUrlVal)
      withReceiptHandle(receiptHandle)
    }

    try {
      sqsClient.deleteMessage(deleteMessageRequest)
    } catch (ex: Exception) {
      println(ex.message)
      throw RuntimeException()
    }
  }

  /**
   * キューの中身を全て削除
   *
   * @param queueUrlVal
   */
  suspend fun deleteMessages(queueUrlVal: String) {
    val purgeRequest = PurgeQueueRequest()
    purgeRequest.run {
      withQueueUrl(queueUrlVal)
    }

    try {
      sqsClient.purgeQueue(purgeRequest)
    } catch (ex: Exception) {
      println(ex.message)
      throw RuntimeException()
    }
  }
}