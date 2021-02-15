package de.cotech.hw.fido.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.cotech.hw.SecurityKeyManager
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var webauthnFragment: WebauthnFragment
    private lateinit var fidoU2fFragment: FidoU2fFragment
    private lateinit var webViewFragment: WebViewFragment
    private lateinit var sweetspotFragment: SweetspotFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val viewPager = findViewById<ViewPager2>(R.id.pager)

        webauthnFragment = WebauthnFragment.newInstance()
        fidoU2fFragment = FidoU2fFragment.newInstance()
        webViewFragment = WebViewFragment.newInstance()
        sweetspotFragment = SweetspotFragment.newInstance()

        val fragmentList = arrayListOf(
                webauthnFragment,
                fidoU2fFragment,
                webViewFragment,
                sweetspotFragment
        )
        viewPager.adapter = ViewPagerAdapter(this, fragmentList)
        viewPager.isUserInputEnabled = false; // disable swiping

        val securityKeyManager = SecurityKeyManager.getInstance()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                securityKeyManager.clearConnectedSecurityKeys()
            }
        })

        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> { tab.text = "FIDO2 / Webauthn" }
                1 -> { tab.text = "U2F" }
                2 -> { tab.text = "WebAuthn" }
                3 -> { tab.text = "NFC" }
            }
        }.attach()
    }

    class ViewPagerAdapter(fa: FragmentActivity, private val fragments: ArrayList<Fragment>) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
}