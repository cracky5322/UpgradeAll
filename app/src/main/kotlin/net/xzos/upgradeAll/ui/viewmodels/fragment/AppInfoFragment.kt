package net.xzos.upgradeAll.ui.viewmodels.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.android.synthetic.main.fragment_app_info.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.xzos.upgradeAll.R
import net.xzos.upgradeAll.database.RepoDatabase
import net.xzos.upgradeAll.server.ServerContainer.Companion.AppManager
import net.xzos.upgradeAll.server.app.manager.module.Updater
import net.xzos.upgradeAll.ui.activity.AppSettingActivity
import net.xzos.upgradeAll.utils.IconPalette
import org.litepal.LitePal
import org.litepal.extension.find


/**
 * 更新项详细数据展示页面
 * 作为框架嵌套到 主页[net.xzos.upgradeAll.ui.activity.MainActivity]
 * 由点击 更新项 [net.xzos.upgradeAll.ui.viewmodels.adapters.AppItemAdapter] 动作 触发显示
 * 使用 [net.xzos.upgradeAll.ui.activity.MainActivity.setFrameLayout] 方法跳转
 */
class AppInfoFragment : Fragment() {
    private var appDatabaseId: Long = 0
    private var versioningPosition: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            appDatabaseId = it.getLong(APP_DATABASE_ID)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_app_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAllAppInfo()
        loadAppVersioningInfo(0)
        editImageView.setOnClickListener {
            it.context.startActivity(
                    Intent(it.context, AppSettingActivity::class.java).apply {
                        this.putExtra("database_id", appDatabaseId)
                    }
            )
        }
        versioningSelectLayout.let { layout ->
            layout.visibility = View.GONE
            GlobalScope.launch {
                val versioningList = getVersioningList()
                runBlocking(Dispatchers.Main) {
                    layout.setOnClickListener { view ->
                        // 选择版本号
                        PopupMenu(view.context, view).let { popupMenu ->
                            for (i in versioningList.indices)
                                popupMenu.menu.add(versioningList[i]).let {
                                    it.setOnMenuItemClickListener {
                                        loadAppVersioningInfo(i)
                                        true
                                    }
                                }
                            popupMenu.show()
                        }
                    }
                    layout.visibility = View.VISIBLE
                }
            }
        }
        activity?.apply {
            this as AppCompatActivity
            this.findViewById<FloatingActionButton>(R.id.addFloatingActionButton)?.visibility = View.GONE
            this.findViewById<FloatingActionButton>(R.id.floatingActionButton)?.let { fab ->
                fab.setOnClickListener {
                    showDownloadDialog()
                }
                fab.setImageDrawable(IconPalette.fabDownloadIcon)
                fab.backgroundTintList = ColorStateList.valueOf((IconPalette.getColorInt(R.color.coolapk_green)))
                fab.setColorFilter(IconPalette.getColorInt(R.color.white))
                fab.visibility = View.VISIBLE
            }
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        (activity as AppCompatActivity).findViewById<FloatingActionButton>(R.id.floatingActionButton)?.visibility = View.GONE
    }

    private fun showDownloadDialog() {
        val versioningPosition = this.versioningPosition
        context?.let {
            BottomSheetDialog(it).let { dialog ->
                dialog.setContentView(
                        layoutInflater.inflate(R.layout.list_content, null)
                )
                dialog.show()
                dialog.findViewById<LinearLayout>(R.id.progressContainer)?.visibility = View.VISIBLE
                GlobalScope.launch {
                    val engine = AppManager.getApp(appDatabaseId).engine
                    val itemList = engine.getReleaseDownload(versioningPosition).keys.toList()
                    runBlocking(Dispatchers.Main) {
                        if (itemList.isNotEmpty()) {
                            dialog.findViewById<ListView>(android.R.id.list)?.let { list ->
                                list.adapter =
                                        ArrayAdapter(dialog.context, android.R.layout.simple_list_item_1, itemList)
                                list.setOnItemClickListener { _, _, i, _ ->
                                    // 下载文件
                                    GlobalScope.launch { Updater(engine).downloadReleaseFile(Pair(versioningPosition, i)) }
                                }
                            }
                        } else {
                            dialog.findViewById<TextView>(R.id.isEmptyTextView)?.visibility = View.VISIBLE
                        }
                        dialog.findViewById<LinearLayout>(R.id.progressContainer)?.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun getVersioningList(): List<String> {
        val engine = AppManager.getApp(appDatabaseId).engine
        return mutableListOf<String>().apply {
            runBlocking(Dispatchers.Default) {
                val versioningNum = engine.getReleaseNum()
                for (i in 0 until versioningNum) {
                    engine.getVersioning(i)?.let {
                        this@apply.add(it)
                    }
                }
            }

        }
    }

    private fun loadAllAppInfo() {
        LitePal.find<RepoDatabase>(appDatabaseId)?.let { appDatabase ->
            GlobalScope.launch {
                val app = AppManager.getApp(appDatabaseId)
                val installedVersioning = app.installedVersioning
                runBlocking(Dispatchers.Main) {
                    loadAppIconView(appDatabase, appIconImageView)
                    nameTextView.text = appDatabase.name
                    appModuleName.text = appDatabase.versionCheckerGson?.text ?: ""
                    versioningTextView.text = installedVersioning ?: ""
                    localVersioningTextView.text = installedVersioning
                            ?: getString(R.string.null_english)
                    appUrlTextView.let {
                        val url = appDatabase.url
                        val context = it.context
                        it.text = url

                        // 打开指向Url
                        it.setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse(url)
                            val chooser = Intent.createChooser(intent, "请选择浏览器")
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(chooser)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadAppVersioningInfo(versioningPosition: Int) {
        this.versioningPosition = versioningPosition
        LitePal.find<RepoDatabase>(appDatabaseId)?.let {
            GlobalScope.launch {
                val engine = AppManager.getApp(appDatabaseId).engine
                val latestVersioning = engine.getVersioning(versioningPosition)
                val latestChangeLog = engine.getChangelog(versioningPosition)
                runBlocking(Dispatchers.Main) {
                    cloud_versioning_text_view.text = if (versioningPosition == 0) {
                        getString(R.string.latest_versioning)
                    } else {
                        getString(R.string.cloud_versioning)
                    }
                    cloudVersioningTextView.text = latestVersioning
                    appChangelogTextView.text = latestChangeLog
                            ?: getString(R.string.null_english)
                }
            }
        }
    }

    private fun loadAppIconView(appDatabase: RepoDatabase, imageView: ImageView) {
        GlobalScope.launch {
            val (appIconUrl, appModuleName) = Pair(
                    runBlocking { AppManager.getApp(appDatabaseId).engine.getAppIconUrl() }
                    , appDatabase.versionCheckerGson?.text
            )
            launch(Dispatchers.Main) {
                if (appIconUrl != null) {
                    Glide.with(imageView)
                            .load(appIconUrl)
                            .into(imageView)
                } else if (appModuleName != null) {
                    val packageManager = imageView.context.packageManager
                    try {
                        packageManager.getPackageInfo(appModuleName, 0)
                        val icon = packageManager.getApplicationIcon(appModuleName)
                        Glide.with(imageView)
                                .load("")
                                .placeholder(icon)
                                .into(imageView)
                    } catch (e: PackageManager.NameNotFoundException) {
                    }
                }
            }
        }
    }

    companion object {
        internal const val APP_DATABASE_ID = "app_database_id"
    }
}
