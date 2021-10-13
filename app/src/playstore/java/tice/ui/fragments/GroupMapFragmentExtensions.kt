package tice.ui.fragments

import android.content.Context
import tice.utility.GMSAvailability

fun GroupMapFragment.createMapFragment(context: Context) = if (GMSAvailability.gmsAvailable(context)) GoogleMapsContainerFragment() else MapboxMapContainerFragment()
