package tice.utility.ui

import android.content.Context
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import tice.managers.SignedInUserManagerType
import tice.managers.UserManagerType
import tice.models.Team
import tice.models.User
import tice.ui.models.GroupNameData
import tice.utility.provider.NameProvider
import tice.utility.provider.UserDataGeneratorType
import java.util.*

internal class NameProviderTest {

    private val mockUserDataGenerator = mockk<UserDataGeneratorType>()
    private val mockUserManager = mockk<UserManagerType>()
    private val mockSignedInUserManager = mockk<SignedInUserManagerType>()
    private val mockContext: Context = mockk(relaxUnitFun = true)

    private val nameSupplier = NameProvider(mockUserDataGenerator, mockUserManager, mockSignedInUserManager, mockContext)

    private val TEST_PUBLIC_NAME = "publicName"
    private val TEST_PUBLIC_TEAMNAME = "publicNames Gruppe"
    private val TEST_PSEUDONYM = "pseudonym"
    private val TEST_PSEUDONYM_TEAMNAME = "pseudonyms Gruppe"
    private val TEST_TEAM_ID = UUID.randomUUID()
    private val TEST_USER_ID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Nested
    inner class GetUserName {

        @Test
        fun `returns publicName`() = runBlockingTest {
            val mockUserType = mockk<User> {
                every { publicName } returns TEST_PUBLIC_NAME
            }

            val result = nameSupplier.getUserName(mockUserType)

            Assertions.assertEquals(TEST_PUBLIC_NAME, result)
        }

        @Test
        fun `returns generatedName`() = runBlockingTest {
            val mockUserType = mockk<User> {
                every { publicName } returns null
                every { userId } returns TEST_USER_ID
            }

            every { mockUserDataGenerator.generatePseudonym(TEST_USER_ID) } returns TEST_PSEUDONYM

            val result = nameSupplier.getUserName(mockUserType)

            Assertions.assertEquals(TEST_PSEUDONYM, result)
        }
    }

    @Nested
    inner class GetSignedInUserName {

        @Test
        fun `returns publicName`() = runBlockingTest {
            every { mockSignedInUserManager.signedInUser } returns mockk {
                every { publicName } returns TEST_PUBLIC_NAME
                every { mockContext.getString(any()) } returns TEST_PUBLIC_TEAMNAME
            }

            val result = nameSupplier.getSignedInUserTeamName()

            Assertions.assertEquals(TEST_PUBLIC_TEAMNAME, result)
        }

        @Test
        fun `returns generatedName`() = runBlockingTest {
            every { mockSignedInUserManager.signedInUser } returns mockk {
                every { publicName } returns null
                every { userId } returns TEST_USER_ID
                every { mockContext.getString(any()) } returns TEST_PSEUDONYM_TEAMNAME
            }
            every { mockUserDataGenerator.generatePseudonym(TEST_USER_ID) } returns TEST_PSEUDONYM

            val result = nameSupplier.getSignedInUserTeamName()

            Assertions.assertEquals(TEST_PSEUDONYM_TEAMNAME, result)
        }
    }

    @Nested
    inner class getPseudoTeamName {

        @Test
        fun `returns publicName`() = runBlockingTest {
            val mockUserType = mockk<User> {
                every { publicName } returns TEST_PUBLIC_NAME
                every { mockContext.getString(any()) } returns TEST_PUBLIC_TEAMNAME
            }

            val result = nameSupplier.getPseudoTeamName(mockUserType)

            Assertions.assertEquals(GroupNameData.PseudoName(TEST_PUBLIC_TEAMNAME), result)
        }

        @Test
        fun `returns generatedName`() = runBlockingTest {
            val mockUserType = mockk<User> {
                every { publicName } returns null
                every { userId } returns TEST_USER_ID
                every { mockContext.getString(any()) } returns TEST_PSEUDONYM_TEAMNAME
            }

            every { mockUserDataGenerator.generatePseudonym(TEST_USER_ID) } returns TEST_PSEUDONYM

            val result = nameSupplier.getPseudoTeamName(mockUserType)

            Assertions.assertEquals(GroupNameData.PseudoName(TEST_PSEUDONYM_TEAMNAME), result)
        }
    }


    @Nested
    inner class GetTeamName {

        @Test
        fun `returns publicName`() = runBlockingTest {
            val mockTeam = mockk<Team> {
                every { name } returns TEST_PUBLIC_NAME
            }

            val result = nameSupplier.getTeamName(mockTeam)

            Assertions.assertEquals(GroupNameData.TeamName(TEST_PUBLIC_NAME), result)
        }

        @Test
        fun `returns generatedName`() = runBlockingTest {
            val mockTeam = mockk<Team> {
                every { name } returns null
                every { owner } returns TEST_USER_ID
            }

            coEvery { mockUserManager.getOrFetchUser(TEST_USER_ID) } returns mockk {
                every { publicName } returns null
                every { userId } returns TEST_USER_ID
                every { mockContext.getString(any()) } returns TEST_PSEUDONYM_TEAMNAME
            }

            every { mockUserDataGenerator.generatePseudonym(TEST_USER_ID) } returns TEST_PSEUDONYM

            val result = nameSupplier.getTeamName(mockTeam)

            Assertions.assertEquals(GroupNameData.PseudoName(TEST_PSEUDONYM_TEAMNAME), result)
        }
    }

    @Nested
    inner class GetShortName {

        @Test
        fun `empty string`() = runBlockingTest {
            val testString = ""

            val result = nameSupplier.getShortName(testString)

            Assertions.assertEquals("NA", result)
        }

        @Test
        fun `one word with 1 letters`() = runBlockingTest {
            val testString = "a"

            val result = nameSupplier.getShortName(testString)

            Assertions.assertEquals("a", result)
        }

        @Test
        fun `one word with 2 letters`() = runBlockingTest {
            val testString = "ab"

            val result = nameSupplier.getShortName(testString)

            Assertions.assertEquals("ab", result)
        }

        @Test
        fun `one word with 5 letters`() = runBlockingTest {
            val testString = "abcde"

            val result = nameSupplier.getShortName(testString)

            Assertions.assertEquals("ab", result)
        }

        @Test
        fun `two word`() = runBlockingTest {
            val testString = "abcde fghi"

            val result = nameSupplier.getShortName(testString)

            Assertions.assertEquals("af", result)
        }

        @Test
        fun `two word with whitespaces`() = runBlockingTest {
            val testString1 = "             abcde fghi      "
            val testString2 = "abcde fghi      "
            val testString3 = "abcde       fghi      "
            val testString4 = "abcde       fghi"

            val result1 = nameSupplier.getShortName(testString1)
            val result2 = nameSupplier.getShortName(testString2)
            val result3 = nameSupplier.getShortName(testString3)
            val result4 = nameSupplier.getShortName(testString4)

            Assertions.assertEquals("af", result1)
            Assertions.assertEquals("af", result2)
            Assertions.assertEquals("af", result3)
            Assertions.assertEquals("af", result4)
        }

        @Test
        fun `three word with whitespaces`() = runBlockingTest {
            val testString1 = "             abcde fghi    jkl  "
            val testString2 = "abcde fghi    jkl  "
            val testString3 = "abcde       fghi    jkl  "
            val testString4 = "abcde       fghi jkl"

            val result1 = nameSupplier.getShortName(testString1)
            val result2 = nameSupplier.getShortName(testString2)
            val result3 = nameSupplier.getShortName(testString3)
            val result4 = nameSupplier.getShortName(testString4)

            Assertions.assertEquals("aj", result1)
            Assertions.assertEquals("aj", result2)
            Assertions.assertEquals("aj", result3)
            Assertions.assertEquals("aj", result4)
        }
    }
}