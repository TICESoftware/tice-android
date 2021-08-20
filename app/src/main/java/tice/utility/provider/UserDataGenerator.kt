package tice.utility.provider

import android.content.Context
import android.graphics.Color
import com.ticeapp.TICE.R
import tice.dagger.scopes.AppScope
import tice.models.UserId
import java.util.*
import javax.inject.Inject

@AppScope
class UserDataGenerator @Inject constructor(val context: Context) : UserDataGeneratorType {

    private val pseudoNames: Array<String> = context.resources.getStringArray(R.array.pseudonyms)
    private val colorPalette: Array<String> = context.resources.getStringArray(R.array.user_color_palette)

    override fun generatePseudonym(userId: UserId): String {
        val userIdInt = userId.toString().toUpperCase(Locale.getDefault()).hashCode()
        val index = (Math.abs(userIdInt)).rem(400)

        return pseudoNames[index]
    }

    override fun generateColor(userId: UserId): Int {
        val userIdInt = userId.toString().uppercase(Locale.getDefault()).hashCode()
        val index = (Math.abs(userIdInt)).rem(19)

        return Color.parseColor(colorPalette[index])
    }
}
