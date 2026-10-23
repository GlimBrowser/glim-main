package cn.ac.oac.glimbrowser.bookmark

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import cn.ac.oac.glimbrowser.data.GlimBrowserDatabase
import cn.ac.oac.glimbrowser.data.GlimRepository
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 文件夹管理：新建空文件夹、改名联动书签、删除只移分类不删书签。 */
class FolderStoreTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("allowExtension", "sq") })
    private val repo: GlimRepository

    init {
        GlimBrowserDatabase.Schema.create(driver)
        repo = GlimRepository(driver)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun createEmptyFolderVisible() {
        val store = FolderStore(repo)
        store.create("待读")
        assertEquals(0, store.folders()["待读"])
    }

    @Test
    fun renameMovesBookmarks() {
        repo.addBookmark("https://a.example/1", "A", "旧名", 1)
        val store = FolderStore(repo)
        store.rename("旧名", "新名")
        assertEquals(1, store.folders()["新名"])
        assertTrue(repo.listBookmarks().all { it.folder == "新名" })
    }

    @Test
    fun deleteFolderKeepsBookmarksInUncategorized() {
        repo.addBookmark("https://b.example/1", "B", "临时", 1)
        val store = FolderStore(repo)
        store.delete("临时")
        val all = repo.listBookmarks()
        assertEquals(1, all.size)
        assertEquals("", all.first().folder)
        assertTrue(store.folders().keys.none { it == "临时" })
    }

    @Test
    fun multipleFoldersCountedAndSorted() {
        repo.addBookmark("https://x/1", "x", "B组", 1)
        repo.addBookmark("https://x/2", "y", "A组", 2)
        repo.addBookmark("https://x/3", "z", "A组", 3)
        val folders = FolderStore(repo).folders()
        assertEquals(listOf("A组", "B组"), folders.keys.filter { it.isNotBlank() }.toList())
        assertEquals(2, folders["A组"])
        assertEquals(1, folders["B组"])
    }
}
