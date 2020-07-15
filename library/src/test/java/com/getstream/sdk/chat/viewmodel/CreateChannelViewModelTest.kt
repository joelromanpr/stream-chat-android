package com.getstream.sdk.chat.viewmodel

import androidx.arch.core.executor.testing.InstantExecutorExtension
import com.getstream.sdk.chat.createChannel
import com.getstream.sdk.chat.createUser
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.models.Channel
import io.getstream.chat.android.client.utils.Result
import io.getstream.chat.android.livedata.ChatDomain
import io.getstream.chat.android.livedata.usecase.CreateChannel
import io.getstream.chat.android.livedata.usecase.UseCaseHelper
import io.getstream.chat.android.livedata.utils.Call2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.amshove.kluent.shouldContainSame
import org.junit.After
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private const val CID = "CID:messaging"
private val CURRENT_USER = createUser(online = true)
private val CHANNEL = createChannel(CID)

@ExperimentalCoroutinesApi
@ExtendWith(InstantExecutorExtension::class)
internal class CreateChannelViewModelTest {
    private val testCoroutineDispatcher = TestCoroutineDispatcher()
    private val testCoroutineScope = TestCoroutineScope(testCoroutineDispatcher)

    private val chatClient: ChatClient = mock()
    private val chatDomain: ChatDomain = mock()
    private val useCases: UseCaseHelper = mock()
    private val createChannel: CreateChannel = mock()
    private val createChannelCall: Call2<Channel> = mock()
    private val createChannelResult: Result<Channel> = mock()

    @BeforeEach
    fun setup() {
        whenever(chatDomain.currentUser) doReturn CURRENT_USER
        whenever(chatDomain.useCases) doReturn useCases
        whenever(useCases.createChannel) doReturn createChannel
        whenever(createChannel.invoke(any())) doReturn createChannelCall
        whenever(createChannelCall.execute()) doReturn createChannelResult
        whenever(createChannelResult.data()) doReturn CHANNEL
        whenever(createChannelResult.isError) doReturn false
        whenever(createChannelResult.isSuccess) doReturn true

        Dispatchers.setMain(testCoroutineDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testCoroutineScope.cleanupTestCoroutines()
    }

    @Test
    fun `Should inform about validation error`() {
        whenever(createChannelResult.isError) doReturn false
        whenever(createChannelResult.isSuccess) doReturn true
        val channelNameCandidates =
                listOf("", ":", "?", "@", "&", "$", "#", "#", "$", "%", "^", "&", "*", "(", ")", "+", "|", "\\", "/", ".", ",", "~", "`", "£", "§", "=")

        channelNameCandidates.forEach {
            val viewModel = CreateChannelViewModel(domain = chatDomain, client = chatClient)
            val states = viewModel.state.observeAll()
            viewModel.onEvent(CreateChannelViewModel.Event.ChannelNameSubmitted(it))
            states shouldContainSame listOf(CreateChannelViewModel.State.ValidationError)
        }

    }

    @Test
    fun `Should inform about backend error`() = testCoroutineScope.runBlockingTest {
        whenever(createChannelResult.isError) doReturn true
        whenever(createChannelResult.isSuccess) doReturn false
        val viewModel = CreateChannelViewModel(domain = chatDomain, client = chatClient, ioDispatcher = testCoroutineDispatcher)
        val states = viewModel.state.observeAll()
        val channelNameCandidate = "channel name"
        viewModel.onEvent(CreateChannelViewModel.Event.ChannelNameSubmitted(channelNameCandidate))

        states shouldContainSame listOf(CreateChannelViewModel.State.Loading, CreateChannelViewModel.State.BackendError)
    }

    @Test
    fun `Should inform about channel creation success`() = testCoroutineScope.runBlockingTest {
        val viewModel = CreateChannelViewModel(domain = chatDomain, client = chatClient, ioDispatcher = testCoroutineDispatcher)
        val states = viewModel.state.observeAll()
        val channelNameCandidate = "channel name"

        viewModel.onEvent(CreateChannelViewModel.Event.ChannelNameSubmitted(channelNameCandidate))

        states shouldContainSame listOf(CreateChannelViewModel.State.Loading, CreateChannelViewModel.State.ChannelCreated)
    }
}