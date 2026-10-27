package com.example.mocklocationtester

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val green = Color.rgb(22,115,78)
    private val ink = Color.rgb(25,43,37)
    private val muted = Color.rgb(97,115,106)
    private val surface = Color.rgb(233,239,232)
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var search: EditText
    private lateinit var results: LinearLayout
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var address: TextView
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var searchButton: ImageButton
    private lateinit var map: WebView
    private var mapReady = false
    private var selected: Place? = null
    private var searchGeneration = 0
    private var searching = false
    private var starting = false
    private var searchTimeout: Runnable? = null
    private lateinit var savedPlaces: SavedPlaces
    private lateinit var favoriteButton: Button
    private lateinit var renameButton: Button
    private var suppressSearchChanges = false
    private var favoritesTab = false
    private var savedQuery = ""
    private var savedPanelOpen = false
    private var visibleSearchResults = emptyList<Place>()
    private val refresh = object : Runnable {
        override fun run() {
            val active = MockLocationService.running
            if (active || MockLocationService.status.startsWith("Failed") || MockLocationService.status.startsWith("Permission") || MockLocationService.status.startsWith("Mock failed")) starting = false
            status.text = if (starting) "Starting mock location…" else MockLocationService.status
            status.setTextColor(if (active) green else muted)
            start.isEnabled = selected != null && !active && !starting
            stop.isEnabled = active || starting
            start.alpha = if (start.isEnabled) 1f else .45f
            stop.alpha = if (stop.isEnabled) 1f else .45f
            handler.postDelayed(this, 350)
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 14) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun text(value: String, size: Float, color: Int = ink) = TextView(this).apply { text = value; textSize = size; setTextColor(color) }
    private fun button(value: String, primary: Boolean = false) = Button(this).apply {
        text = value; textSize = 16f; isAllCaps = false
        setTextColor(if (primary) Color.WHITE else ink)
        background = rounded(if (primary) green else surface)
        minHeight = dp(52); minimumHeight = dp(52)
    }
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedPrefs = getPreferences(MODE_PRIVATE)
        savedPlaces = SavedPlaces(savedPrefs.getString("saved_places_v1", null)) {
            savedPrefs.edit().putString("saved_places_v1", it).apply()
        }
        val root = LinearLayout(this).apply { isFocusableInTouchMode = true; orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1, -1))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(column)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22),dp(16),dp(18),dp(14)) }
        header.addView(text("Where am I ??",23f).apply { setTypeface(null,Typeface.BOLD) },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text("TEST MODE",10f,muted).apply { background=rounded(surface,20);setPadding(dp(9),dp(6),dp(9),dp(6)) })
        header.addView(button("?").apply { contentDescription="Setup guide"; setOnClickListener { showHelp() } },LinearLayout.LayoutParams(dp(44),dp(44)).apply { leftMargin=dp(8) })
        column.addView(header)
        val form=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(dp(18),0,dp(18),dp(14)) }
        search=EditText(this).apply {
            id=R.id.search_input;hint="Search address or place…";textSize=16f;setTextColor(ink);setHintTextColor(muted)
            setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT;imeOptions=EditorInfo.IME_ACTION_SEARCH
            setBackgroundColor(Color.TRANSPARENT);setPadding(dp(12),0,dp(8),0)
            setOnEditorActionListener { _,action,_ -> if(action==EditorInfo.IME_ACTION_SEARCH){searchPlaces();true}else false }
        }
        val clearSearch=ImageButton(this).apply {
            setImageResource(R.drawable.ic_clear_search)
            setColorFilter(muted)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(15),dp(15),dp(15),dp(15))
            contentDescription="Clear search text"
            visibility=View.GONE
            setOnClickListener {
                search.text.clear()
                search.error=null
                search.requestFocus()
                getSystemService(InputMethodManager::class.java).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT)
            }
        }
        search.addTextChangedListener(object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                clearSearch.visibility=if(s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if(searching) {
                    searchGeneration++;searching=false;searchButton.isEnabled=true
                    searchTimeout?.let{handler.removeCallbacks(it)}
                }
                if (::results.isInitialized && !suppressSearchChanges) {
                    visibleSearchResults = emptyList()
                    if (search.hasFocus()) showSavedPlaces(s?.toString().orEmpty()) else results.removeAllViews()
                }
            }
            override fun afterTextChanged(s:Editable?) {}
        })
        val searchField=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            background=rounded(Color.WHITE).apply {setStroke(dp(1),Color.rgb(209,221,211))}
            addView(search,LinearLayout.LayoutParams(0,-1,1f))
            addView(clearSearch,LinearLayout.LayoutParams(dp(48),dp(48)).apply {marginEnd=dp(2)})
        }
        form.addView(searchField,LinearLayout.LayoutParams(0,dp(52),1f))
        searchButton=ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_search);setColorFilter(Color.WHITE);background=rounded(green)
            contentDescription="Search address";setOnClickListener { searchPlaces() }
        }
        form.addView(searchButton,LinearLayout.LayoutParams(dp(52),dp(52)).apply {leftMargin=dp(8)})
        column.addView(form)
        results=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)}
        val shortcuts = LinearLayout(this).apply { setPadding(dp(18),0,dp(18),dp(8)) }
        shortcuts.addView(button("History").apply { setOnClickListener { favoritesTab=false;cancelSearch();hideKeyboard();showSavedPlaces() } },LinearLayout.LayoutParams(0,dp(48),1f))
        shortcuts.addView(button("★ Favorites").apply { setOnClickListener { favoritesTab=true;cancelSearch();hideKeyboard();showSavedPlaces() } },LinearLayout.LayoutParams(0,dp(48),1f).apply{leftMargin=dp(8)})
        column.addView(shortcuts)
        val resultScroll = object : ScrollView(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(dp(260), View.MeasureSpec.AT_MOST))
            }
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(event.actionMasked!=MotionEvent.ACTION_UP && event.actionMasked!=MotionEvent.ACTION_CANCEL)
                return super.dispatchTouchEvent(event)
            }
        }.apply { addView(results) }
        column.addView(resultScroll)
        search.setOnClickListener { favoritesTab=false;cancelSearch();showSavedPlaces() }
        search.setOnFocusChangeListener { _, focused -> if(focused && !suppressSearchChanges) { favoritesTab=false;cancelSearch();showSavedPlaces() } }
        map=WebView(this).apply {
            setBackgroundColor(surface)
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString="WhereAmI/1.5 (Android; com.example.mocklocationtester)"
            settings.cacheMode=WebSettings.LOAD_DEFAULT
            webViewClient=object:WebViewClient(){
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? {
                    val uri=request.url
                    if(uri.scheme=="https" && uri.host=="appassets.androidplatform.net") {
                        val file=uri.path?.removePrefix("/") ?: ""
                        if(file in setOf("map.html","leaflet.js","leaflet.css")) {
                            val mime=when {file.endsWith(".js")->"application/javascript";file.endsWith(".css")->"text/css";else->"text/html"}
                            return WebResourceResponse(mime,"UTF-8",assets.open(file))
                        }
                    }
                    if(uri.scheme=="https" && uri.host=="tile.openstreetmap.org") return null
                    return WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
                }
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean {
                    if(request.url.host=="www.openstreetmap.org" && request.url.scheme=="https") {
                        try { startActivity(Intent(Intent.ACTION_VIEW,request.url)) } catch (_:Exception) { toast("No browser available.") }
                    }
                    return true
                }
                override fun onPageFinished(view:WebView,url:String) {mapReady=true;updatePin()}
            }
            setOnTouchListener { view,event ->
                view.parent.requestDisallowInterceptTouchEvent(event.actionMasked!=MotionEvent.ACTION_UP && event.actionMasked!=MotionEvent.ACTION_CANCEL)
                false
            }
        }
        column.addView(map,LinearLayout.LayoutParams(-1,dp((resources.configuration.screenHeightDp*.42).toInt().coerceIn(240,380))))
        val bottom=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(22),dp(22),dp(22))}
        bottom.addView(text("SELECTED LOCATION",11f,muted).apply {letterSpacing=.08f})
        title=text("Choose a location",22f).apply {setPadding(0,dp(10),0,dp(6));setTypeface(null,Typeface.BOLD)};bottom.addView(title)
        address=text("Search for an address to choose a point on the map.",14f,muted);bottom.addView(address)
        val favoriteActions = LinearLayout(this).apply {setPadding(0,dp(12),0,0)}
        favoriteButton=button("☆ Add to favorites").apply {isEnabled=false;setOnClickListener {selected?.let{toggleFavorite(it)}}}
        renameButton=button("Rename").apply {visibility=View.GONE;setOnClickListener{selected?.let{renameFavorite(it)}}}
        favoriteActions.addView(favoriteButton,LinearLayout.LayoutParams(0,-2,1f))
        favoriteActions.addView(renameButton,LinearLayout.LayoutParams(-2,-2).apply{leftMargin=dp(8)})
        bottom.addView(favoriteActions)
        status=text("Mock location is inactive",12f,muted).apply {setPadding(0,dp(20),0,dp(14));accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE};bottom.addView(status)
        val actions=LinearLayout(this)
        start=button("▶  Start",true).apply {isEnabled=false;setOnClickListener{startMock()}}
        stop=button("■  Stop").apply {isEnabled=false;setOnClickListener{starting=false;val intent=Intent(this@MainActivity,MockLocationService::class.java);stopService(intent)}}
        actions.addView(start,LinearLayout.LayoutParams(0,dp(54),1.5f))
        actions.addView(stop,LinearLayout.LayoutParams(0,dp(54),1f).apply{leftMargin=dp(10)})
        bottom.addView(actions);column.addView(bottom)
        val prefs=getPreferences(MODE_PRIVATE)
        val lat=prefs.getString("lat",null)?.toDoubleOrNull();val lon=prefs.getString("lon",null)?.toDoubleOrNull()
        if(lat!=null && lon!=null && lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
            selected=Place(prefs.getString("name","Saved location")!!,prefs.getString("address","$lat, $lon")!!,lat,lon)
            if (!prefs.contains("saved_places_v1")) selected?.let { savedPlaces.record(it) }
            renderSelection()
        }
        map.loadUrl("https://appassets.androidplatform.net/map.html")
    }
    private fun searchPlaces() {
        val query=search.text.toString().trim()
        if(query.length<3){search.error="Enter at least 3 characters";return}
        if(searching)return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken,0)
        savedPanelOpen=false;visibleSearchResults=emptyList()
        val generation=++searchGeneration;searching=true;searchButton.isEnabled=false
        results.removeAllViews();results.addView(text("Searching for an address…",14f,muted).apply{setPadding(0,0,0,dp(12))})
        searchTimeout=Runnable { finishSearch(generation,emptyList(),"Search timed out. Try again or enter latitude, longitude.") }.also{handler.postDelayed(it,15000)}
        val coords=query.split(',').map{it.trim().toDoubleOrNull()}
        if(coords.size==2 && coords.all{it!=null && it.isFinite()} && coords[0]!! in -90.0..90.0 && coords[1]!! in -180.0..180.0) {
            finishSearch(generation,listOf(Place("Coordinates",query,coords[0]!!,coords[1]!!)));return
        }
        if(!Geocoder.isPresent()){finishSearch(generation,emptyList(),"Address search is unavailable on this device. Enter latitude, longitude.");return}
        val geocoder=Geocoder(this,Locale.ENGLISH)
        fun deliver(addresses:List<android.location.Address>) {
            val places=addresses.filter{it.hasLatitude() && it.hasLongitude() && it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0}.map{
                Place(it.featureName ?: it.thoroughfare ?: query,it.getAddressLine(0) ?: query,it.latitude,it.longitude)
            }
            handler.post{finishSearch(generation,places)}
        }
        if(Build.VERSION.SDK_INT>=33) {
            try {geocoder.getFromLocationName(query,4,object:Geocoder.GeocodeListener {
                override fun onGeocode(addresses:MutableList<android.location.Address>){deliver(addresses)}
                override fun onError(errorMessage:String?){handler.post{finishSearch(generation,emptyList(),"Address search failed. Check your internet connection and try again.")}}
            })} catch(_:Exception){finishSearch(generation,emptyList(),"Search is unavailable. Try again.")}
        } else executor.execute {
            try {
                @Suppress("DEPRECATION") val addresses=geocoder.getFromLocationName(query,4).orEmpty()
                deliver(addresses)
            } catch(_:Exception){handler.post{finishSearch(generation,emptyList(),"Address search failed. Check your internet connection and try again.")}}
        }
    }
    private fun finishSearch(generation:Int,places:List<Place>,error:String?=null) {
        if(isDestroyed || generation!=searchGeneration || !searching)return
        searching=false;searchButton.isEnabled=true;searchTimeout?.let{handler.removeCallbacks(it)}
        results.removeAllViews()
        if(places.isEmpty())results.addView(text(error ?: "Address not found. Include the city name.",14f,muted).apply{setPadding(0,0,0,dp(14))})
        visibleSearchResults=places
        places.forEach { addPlaceRow(it, false) }
    }
    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(search.windowToken,0)
    }
    private fun cancelSearch() {
        searchGeneration++;searching=false;searchButton.isEnabled=true
        searchTimeout?.let { handler.removeCallbacks(it) }
    }
    private fun selectPlace(place: Place) {
        cancelSearch()
        if(MockLocationService.running || starting) {
            val serviceIntent=Intent(this,MockLocationService::class.java)
            stopService(serviceIntent)
        }
        starting=false;selected=place;savedPlaces.record(place)
        suppressSearchChanges=true
        search.setText(savedPlaces.displayName(place));search.setSelection(search.text.length);search.error=null;search.clearFocus()
        suppressSearchChanges=false
        hideKeyboard();savedPanelOpen=false;visibleSearchResults=emptyList();results.removeAllViews()
        renderSelection();updatePin()
        getPreferences(MODE_PRIVATE).edit().putString("lat",place.lat.toString()).putString("lon",place.lon.toString()).putString("name",place.name).putString("address",place.address).apply()
    }
    private fun addPlaceRow(place: Place, allowRename: Boolean) {
        val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        row.addView(button(savedPlaces.displayName(place)+"\n"+place.address).apply {
            textSize=13f;gravity=Gravity.START or Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10))
            setOnClickListener {selectPlace(place)}
        },LinearLayout.LayoutParams(0,-2,1f))
        row.addView(button(if(savedPlaces.favorite(place)!=null) "★" else "☆").apply {
            contentDescription=(if(savedPlaces.favorite(place)!=null) "Remove favorite: " else "Add favorite: ")+savedPlaces.displayName(place)
            setOnClickListener {toggleFavorite(place)}
        },LinearLayout.LayoutParams(dp(48),dp(48)).apply{leftMargin=dp(4)})
        if(allowRename) row.addView(button("⋮").apply {
            contentDescription="Favorite options: "+savedPlaces.displayName(place)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle(savedPlaces.displayName(place))
                    .setItems(arrayOf("Rename", "Remove from favorites")) {_,which ->
                        if(which==0) renameFavorite(place) else toggleFavorite(place)
                    }.show()
            }
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        results.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
    }
    private fun showSavedPlaces(query: String = "") {
        savedPanelOpen=true;savedQuery=query;visibleSearchResults=emptyList();results.removeAllViews()
        val heading=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        heading.addView(text(if(favoritesTab) "FAVORITES" else "RECENT LOCATIONS",12f,muted),LinearLayout.LayoutParams(0,-2,1f))
        if(!favoritesTab && savedPlaces.history().isNotEmpty()) heading.addView(button("Clear").apply {
            textSize=12f;contentDescription="Clear location history"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Clear history?")
                    .setMessage("Your favorites will be kept.").setNegativeButton("Cancel",null)
                    .setPositiveButton("Clear") {_,_->savedPlaces.clearHistory();showSavedPlaces(savedQuery)}.show()
            }
        })
        heading.addView(button("×").apply {contentDescription="Close saved locations";setOnClickListener{savedPanelOpen=false;results.removeAllViews();hideKeyboard()}},LinearLayout.LayoutParams(dp(48),dp(48)))
        results.addView(heading)
        val places=if(favoritesTab) savedPlaces.favorites(query).map{it.place} else savedPlaces.history(query)
        if(places.isEmpty()) results.addView(text(
            if(query.isNotBlank()) "No saved matches. Tap Search to find a new place."
            else if(favoritesTab) "No favorites yet. Tap ☆ next to a location to save it."
            else "No history yet. Search for a place and select a result.",14f,muted
        ).apply{setPadding(0,dp(8),0,dp(16))})
        places.forEach {addPlaceRow(it,favoritesTab)}
    }
    private fun refreshSavedViews() {
        renderSelection()
        if(savedPanelOpen) showSavedPlaces(savedQuery)
        else if(visibleSearchResults.isNotEmpty()) {
            results.removeAllViews();visibleSearchResults.forEach{addPlaceRow(it,false)}
        }
    }
    private fun toggleFavorite(place: Place) {
        savedPlaces.toggleFavorite(place);refreshSavedViews()
        toast(if(savedPlaces.favorite(place)!=null) "Added to favorites" else "Removed from favorites")
    }
    private fun renameFavorite(place: Place) {
        val existing=savedPlaces.favorite(place) ?: return
        val input=EditText(this).apply {
            setSingleLine(true);hint="Favorite name";contentDescription="Favorite name"
            filters=arrayOf(InputFilter.LengthFilter(80));setText(existing.label);selectAll()
            setPadding(dp(20),dp(12),dp(20),dp(12))
        }
        val dialog=AlertDialog.Builder(this).setTitle("Rename favorite").setView(input)
            .setNegativeButton("Cancel",null).setPositiveButton("Save",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name=input.text.toString().trim()
                if(name.isEmpty()) {input.error="Enter a name";return@setOnClickListener}
                savedPlaces.rename(place,name)
                if(selected?.key==place.key) {
                    suppressSearchChanges=true;search.setText(name);suppressSearchChanges=false
                }
                refreshSavedViews();dialog.dismiss()
            }
            input.requestFocus();dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }
    private fun renderSelection(){selected?.let{
        title.text=savedPlaces.displayName(it);address.text=it.address
        favoriteButton.isEnabled=true
        favoriteButton.text=if(savedPlaces.favorite(it)!=null) "★ Favorited" else "☆ Add to favorites"
        favoriteButton.contentDescription=if(savedPlaces.favorite(it)!=null) "Remove selected location from favorites" else "Add selected location to favorites"
        renameButton.visibility=if(savedPlaces.favorite(it)!=null) View.VISIBLE else View.GONE
    }}
    private fun updatePin(){selected?.let{if(mapReady)map.evaluateJavascript("window.showLocation(${it.lat},${it.lon});",null)}}
    private fun toast(message:String){Toast.makeText(this,message,Toast.LENGTH_LONG).show()}
    private fun startMock(){
        val place=selected ?: return
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),10);toast("Allow location access, then tap Start again.");return
        }
        if(!getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)){toast("Turn on device location first.");startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),11)
        try {starting=true;startForegroundService(Intent(this,MockLocationService::class.java).putExtra("lat",place.lat).putExtra("lon",place.lon));handler.postDelayed({starting=false},4000)}
        catch(e:Exception){starting=false;MockLocationService.status="Failed to start: ${e.localizedMessage}"}
    }
    private fun showHelp(){
        AlertDialog.Builder(this).setTitle("Set up mock location")
            .setMessage("1. Settings > About phone > tap Build number 7 times.\n2. Developer options > Select mock location app > Where am I ??.\n3. Turn on Location. Search for an address, select a result, then tap Start.\n\nLocations remain marked as mock. The map requires internet access and uses OpenStreetMap. Searches are sent to the device geocoding service. This app has no backend or login.")
            .setPositiveButton("Developer Options"){_,_->try{startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))}catch(_:Exception){startActivity(Intent(Settings.ACTION_SETTINGS))}}
            .setNegativeButton("Close",null).show()
    }
    override fun onResume(){super.onResume();if(::map.isInitialized)map.onResume();handler.post(refresh)}
    override fun onPause(){handler.removeCallbacks(refresh);if(::map.isInitialized)map.onPause();super.onPause()}
    override fun onDestroy(){searchGeneration++;handler.removeCallbacksAndMessages(null);executor.shutdownNow();if(::map.isInitialized){(map.parent as? android.view.ViewGroup)?.removeView(map);map.destroy()};super.onDestroy()}
}
