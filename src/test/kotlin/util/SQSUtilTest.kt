package util

import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry
import java.util.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SQSUtilTest {
  companion object {
    private const val QUEUE_URL1 = "http://localhost:9324/queue/sample1"
    private const val QUEUE_URL2 = "http://localhost:9324/queue/sample2"
    private const val QUEUE_URL3 = "http://localhost:9324/queue/sample3"
    private const val FIFO_QUEUE_URL1 = "http://localhost:9324/queue/sample-fifo1.fifo"
    private const val FIFO_QUEUE_URL2 = "http://localhost:9324/queue/sample-fifo2.fifo"
  }

  @BeforeEach
  fun setUp() {
    runBlocking { SQSUtil.deleteMessages(queueUrlVal = QUEUE_URL1) }
    runBlocking { SQSUtil.deleteMessages(queueUrlVal = QUEUE_URL2) }
    runBlocking { SQSUtil.deleteMessages(queueUrlVal = QUEUE_URL3) }
    runBlocking { SQSUtil.deleteMessages(queueUrlVal = FIFO_QUEUE_URL1) }
    runBlocking { SQSUtil.deleteMessages(queueUrlVal = FIFO_QUEUE_URL2) }
  }

  @Nested
  inner class 通常キューの場合 {
    @Test
    fun 送信したメッセージの内容が正しいこと() {
      runBlocking { SQSUtil.sendMessages(queueUrlVal = QUEUE_URL1, message = "テスト") }
      val actual =
        runBlocking {
          SQSUtil.receiveMessages(
            queueUrlVal = QUEUE_URL1,
            maxNumberOfMessages = 1
          )
        }[0]
      assertThat(actual.body).isEqualTo("テスト")
    }

    @Test
    fun メッセージが一括で送信できること() {
      val message1 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageBody("テスト1")
      }
      val message2 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageBody("テスト2")
      }

      runBlocking {
        SQSUtil.sendBatchMessages(
          queueUrlVal = QUEUE_URL1,
          messages = listOf(message1, message2)
        )
      }

      val actual =
        runBlocking { SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL1, maxNumberOfMessages = 10) }
      assertThat(actual.size).isEqualTo(2)
    }

  }

  @Nested
  inner class 通常キューの場合_可視性タイムアウト3秒 {
    @Test
    fun メッセージを取得してから3秒間メッセージが取得できないこと() {
      runBlocking { SQSUtil.sendMessages(queueUrlVal = QUEUE_URL2, message = "テスト") }

      val messages1 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL2, maxNumberOfMessages = 10)
      }
      val messages2 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL2, maxNumberOfMessages = 10)
      }
      assertThat(messages1.size).isEqualTo(1)
      assertThat(messages2.size).isEqualTo(0)
    }

    @Test
    fun メッセージを取得してから3秒後にメッセージが取得できること() {
      runBlocking { SQSUtil.sendMessages(queueUrlVal = QUEUE_URL2, message = "テスト") }
      val messages1 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL2, maxNumberOfMessages = 10)
      }
      assertThat(messages1.size).isEqualTo(1)

      Thread.sleep(3000) // 可視性タイムアウトが3秒で設定してあるため3秒待つ

      val messages2 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL2, maxNumberOfMessages = 10)
      }
      assertThat(messages2.size).isEqualTo(1)
    }
  }

  @Nested
  inner class 通常キューの場合_遅延キュー3秒 {
    @Test
    fun メッセージを送信してから3秒経過していない場合メッセージが取得できないこと() {
      runBlocking { SQSUtil.sendMessages(queueUrlVal = QUEUE_URL3, message = "テスト") }
      val messages1 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL3, maxNumberOfMessages = 10)
      }

      assertThat(messages1.size).isEqualTo(0)
    }

    @Test
    fun メッセージを送信してから3秒経過したらメッセージが取得できること() {
      runBlocking { SQSUtil.sendMessages(queueUrlVal = QUEUE_URL3, message = "テスト") }

      Thread.sleep(3000)

      val messages1 = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = QUEUE_URL3, maxNumberOfMessages = 10)
      }
      assertThat(messages1.size).isEqualTo(1)
    }
  }

  @Nested
  inner class FIFOキューの場合_コンテンツに基づく重複排除OFF {
    @Test
    fun メッセージの順序が正しいこと() {
      val messageGroupId = "Group1" // メッセージグループIDは同一にする
      val message1 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageBody("テスト1")
      }
      val message2 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageBody("テスト2")
      }
      runBlocking {
        SQSUtil.sendBatchMessages(
          queueUrlVal = FIFO_QUEUE_URL1,
          messages = listOf(message1, message2)
        )
      }

      val actual = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = FIFO_QUEUE_URL1, maxNumberOfMessages = 10)
      }

      assertThat(actual[0].body).isEqualTo("テスト1")
      assertThat(actual[1].body).isEqualTo("テスト2")
    }

    @Test
    fun 同一グループで同一のメッセージは登録されないこと() {
      val messageGroupId = "Group1" // メッセージグループIDは同一にする
      val message1 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageBody("テスト1")
      }
      val message2 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageBody("テスト1")
      }
      runBlocking {
        SQSUtil.sendBatchMessages(
          queueUrlVal = FIFO_QUEUE_URL1,
          messages = listOf(message1, message2)
        )
      }

      val actual = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = FIFO_QUEUE_URL1, maxNumberOfMessages = 10)
      }

      assertThat(actual.size).isEqualTo(1)
    }
  }

  @Nested
  inner class FIFOキューの場合_FIFOキューの場合_コンテンツに基づく重複排除ON {
    @Test
    fun 重複IDが同一の場合_メッセージの重複が許されないこと() { // 5分間は同一のメッセージ重複IDのメッセージが送信できない
      val messageGroupId = "Group1" // メッセージグループIDは同一にする
      val messageDuplicateId = "DuplicateId"
      val message1 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageDeduplicationId(messageDuplicateId)
        withMessageBody("テスト1")
      }
      val message2 = SendMessageBatchRequestEntry().run {
        withId(UUID.randomUUID().toString())
        withMessageGroupId(messageGroupId)
        withMessageDeduplicationId(messageDuplicateId)
        withMessageBody("テスト2")
      }
      runBlocking {
        SQSUtil.sendBatchMessages(
          queueUrlVal = FIFO_QUEUE_URL2,
          messages = listOf(message1, message2)
        )
      }

      val actual = runBlocking {
        SQSUtil.receiveMessages(queueUrlVal = FIFO_QUEUE_URL2, maxNumberOfMessages = 10)
      }

      assertThat(actual.size).isEqualTo(1) //2件送信しても1件のみ登録されていること
    }
  }
}