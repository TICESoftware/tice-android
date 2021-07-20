package tice.managers.storageManagers

import io.mockk.*
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tice.models.User
import tice.models.database.UserInterface
import java.util.*

internal class UserStorageManagerTest {

    private lateinit var userStorageManager: UserStorageManager

    private val mockAppDatabase: AppDatabase = mockk(relaxUnitFun = true)
    private val mockUserInterface: UserInterface = mockk(relaxUnitFun = true)

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { mockAppDatabase.userInterface() } returns mockUserInterface

        userStorageManager = UserStorageManager(mockAppDatabase)
    }

    @Test
    fun store() = runBlockingTest {
        val mockUser: User = mockk()

        userStorageManager.store(mockUser)

        coVerify(exactly = 1) { mockUserInterface.insert(mockUser) }
    }

    @Test
    fun loadUser() = runBlockingTest {
        val TEST_USER_ID = UUID.randomUUID()
        val mockUser: User = mockk()

        coEvery { mockUserInterface.get(TEST_USER_ID) } returns mockUser

        userStorageManager.loadUser(TEST_USER_ID)

        coVerify(exactly = 1) { mockUserInterface.get(TEST_USER_ID) }
    }
}