package tice.utility.ui

import android.graphics.drawable.InsetDrawable
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView
import com.ticeapp.TICE.R

fun RecyclerView.createDividerWithMargin() {
    val attributes = intArrayOf(android.R.attr.listDivider)

    val styleAttributes = context.obtainStyledAttributes(attributes)
    val divider = styleAttributes.getDrawable(0)
    val inset = resources.getDimensionPixelSize(R.dimen.divider_margin)
    val insetDivider = InsetDrawable(divider, inset, 0, 0, 0)
    styleAttributes.recycle()

    val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
    itemDecoration.setDrawable(insetDivider)

    addItemDecoration(itemDecoration)
}
