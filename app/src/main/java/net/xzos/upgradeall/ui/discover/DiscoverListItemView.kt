package net.xzos.upgradeall.ui.discover

import android.view.View
import net.xzos.upgradeall.core.manager.AppManager
import net.xzos.upgradeall.ui.base.list.ListItemView

class DiscoverListItemView(
        override val name: String,
        val type: Int,
        val hubName: String,
        val uuid: String
) : ListItemView {
    val isSavedIvVisibility = if (AppManager.getAppByUuid(uuid) == null) View.GONE else View.VISIBLE
}