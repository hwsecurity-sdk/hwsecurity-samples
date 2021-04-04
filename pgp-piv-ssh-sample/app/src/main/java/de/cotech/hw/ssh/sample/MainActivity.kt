package de.cotech.hw.ssh.sample

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

    private lateinit var openPgpFragment: OpenPgpFragment
    private lateinit var pivFragment: PivFragment
    private lateinit var sshjFragment: SshjFragment
    private lateinit var jschFragment: JschFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val viewPager = findViewById<ViewPager2>(R.id.pager)

        openPgpFragment = OpenPgpFragment.newInstance()
        pivFragment = PivFragment.newInstance()
        sshjFragment = SshjFragment.newInstance()
        jschFragment = JschFragment.newInstance()

        val fragmentList = arrayListOf(
                openPgpFragment,
                pivFragment,
                sshjFragment,
                jschFragment
        )
        viewPager.adapter = ViewPagerAdapter(this, fragmentList)
        viewPager.isUserInputEnabled = false // disable swiping

        val securityKeyManager = SecurityKeyManager.getInstance()
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                securityKeyManager.clearConnectedSecurityKeys()
            }
        })

        val tabLayout: TabLayout = findViewById(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = "OpenPGP"
                }
                1 -> {
                    tab.text = "PIV"
                }
                2 -> {
                    tab.text = "SSH (SSHJ)"
                }
                3 -> {
                    tab.text = "SSH (Jsch)"
                }
            }
        }.attach()
    }

    class ViewPagerAdapter(fa: FragmentActivity, private val fragments: ArrayList<Fragment>) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]
    }
}