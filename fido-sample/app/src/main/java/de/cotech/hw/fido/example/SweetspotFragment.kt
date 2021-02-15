package de.cotech.hw.fido.example

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class SweetspotFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_sweetspot, container, false)

        view.findViewById<View>(R.id.buttonSweetspot).setOnClickListener { startSweetspotActivity() }

        return view
    }

    private fun startSweetspotActivity() {
        val intent = Intent(activity, SweetspotActivity::class.java)
        startActivity(intent)
    }

    companion object {
        fun newInstance() = SweetspotFragment()
    }

}