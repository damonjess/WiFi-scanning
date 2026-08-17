package com.damon.wifiaudit

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.damon.wifiaudit.data.AppDatabase
import com.damon.wifiaudit.data.oui.OuiCsvImporter
import com.damon.wifiaudit.vendor.OuiVendorLookup
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OuiImportTest {
    @Test
    fun testOuiImportAndLookup() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val db = AppDatabase.getInstance(appContext)
        
        // Reset DB import version to force re-import
        val prefs = appContext.getSharedPreferences("oui_import", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        
        // Clear existing data
        db.ouiVendorDao().clearAll()
        
        val startTime = System.currentTimeMillis()
        OuiCsvImporter.importIfNeeded(appContext)
        val endTime = System.currentTimeMillis()
        
        val count = db.ouiVendorDao().count()
        val duration = endTime - startTime
        
        println("Imported $count OUI entries in $duration ms")
        
        // Verify entries in DB (Persistence Layer)
        val apple = db.ouiVendorDao().lookupVendor("000393")
        assertTrue("Apple should be found in DB", apple?.contains("Apple") == true)
        
        val vendor28 = db.ouiVendorDao().lookupVendor("F0E0D0C")
        assertTrue("28-bit vendor should be found in DB", vendor28?.contains("Example 28-bit") == true)
        
        val vendor36 = db.ouiVendorDao().lookupVendor("F0E0D0C0B")
        assertTrue("36-bit vendor should be found in DB", vendor36?.contains("Example 36-bit") == true)

        // Verify runtime lookup logic (Hierarchical Resolution)
        // Note: OuiVendorLookup may already be initialized, but it loads from the same assets.
        OuiVendorLookup.initialize(appContext)
        
        // 1. Test 36-bit match (most specific)
        val lookup36 = OuiVendorLookup.lookup("F0:E0:D0:C0:B1:23")
        assertTrue("Should resolve 36-bit vendor, got $lookup36", lookup36?.contains("Example 36-bit") == true)
        
        // 2. Test 28-bit match
        val lookup28 = OuiVendorLookup.lookup("F0:E0:D0:C1:23:45")
        assertTrue("Should resolve 28-bit vendor, got $lookup28", lookup28?.contains("Example 28-bit") == true)
        
        // 3. Test 24-bit match (standard)
        val lookup24 = OuiVendorLookup.lookup("00:03:93:AB:CD:EF")
        assertTrue("Should resolve Apple, got $lookup24", lookup24?.contains("Apple") == true)
    }
}
