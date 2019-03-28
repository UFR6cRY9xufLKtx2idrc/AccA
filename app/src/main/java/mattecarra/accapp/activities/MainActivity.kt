package mattecarra.accapp.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.input.getInputField
import com.afollestad.materialdialogs.input.input
import com.github.javiersantos.appupdater.AppUpdater
import com.github.javiersantos.appupdater.enums.Display
import com.github.javiersantos.appupdater.enums.UpdateFrom
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.topjohnwu.superuser.Shell
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.dashboard_fragment.*
import mattecarra.accapp.utils.AccUtils
import mattecarra.accapp.R
import mattecarra.accapp._interface.OnProfileClickListener
import mattecarra.accapp.utils.AccConfig
import mattecarra.accapp.fragments.DashboardFragment
import mattecarra.accapp.fragments.ProfilesFragment
import mattecarra.accapp.fragments.SchedulesFragment
import mattecarra.accapp.models.AccaProfile
import mattecarra.accapp.utils.progress
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class MainActivity : AppCompatActivity(), BottomNavigationView.OnNavigationItemSelectedListener, OnProfileClickListener {
    override fun onProfileClick(accaProfile: AccaProfile) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val LOG_TAG = "MainActivity"
    private val PERMISSION_REQUEST: Int = 0
    private val ACC_CONFIG_EDITOR_REQUEST: Int = 1
    private val ACC_PROFILE_CREATOR_REQUEST: Int = 2
    private val ACC_PROFILE_EDITOR_REQUEST: Int = 3
    private val ACC_PROFILE_SCHEDULER_REQUEST: Int = 4

    private lateinit var mViewModel: MainActivityViewModel

    val mMainFragment = DashboardFragment.newInstance()
    val mProfilesFragment = ProfilesFragment.newInstance()
    val mSchedulesFragment = SchedulesFragment.newInstance()

    // TODO: Check what the mAccConfig does in the MainActivity
    private lateinit var mAccConfig: AccConfig

    private lateinit var mSharedPrefs: SharedPreferences
    // TODO: Use Gson to export profiles, use internal SQLite database to store
    private val mGson: Gson = Gson()

//    private var profilesAdapter: ProfilesViewAdapter? = null
//
//    private var batteryInfo: BatteryInfo? = null
//    private var isDaemonRunning = false

    // TODO: Move schedules to the Schedules Fragment
//    private lateinit var scheduleAdapter: ScheduleRecyclerViewAdapter
//
//    val addSchedule: (Schedule) -> Unit = { schedule ->
//        if(scheduleAdapter.itemCount == 0) {
//            no_schedules_jobs_textview.visibility = View.GONE
//            scheduled_jobs_recyclerview.visibility = View.VISIBLE
//        }
//        scheduleAdapter.add(schedule)
//    }
//
//    val deleteSchedule: (Schedule) -> Unit = { schedule ->
//        AccUtils.deleteSchedule(schedule.executeOnce, schedule.name)
//        scheduleAdapter.remove(schedule)
//
//        if(scheduleAdapter.itemCount == 0) {
//            no_schedules_jobs_textview.visibility = View.VISIBLE
//            scheduled_jobs_recyclerview.visibility = View.GONE
//        }
//    }

//    //Used to update battery info every second
//    private val handler = Handler()
//    private val updateUIRunnable = object : Runnable {
//        override fun run() {
//            val r = this //need this to make it recursive
//            doAsync {
//                val batteryInfo = AccUtils.getBatteryInfo()
//                isDaemonRunning = AccUtils.isAccdRunning()
//                uiThread {
//                    // Run accd UI check
//                    updateAccdStatus(isDaemonRunning)
//
//                    setBatteryInfo(batteryInfo)
//
//                    handler.postDelayed(r, 1000)// Repeat the same runnable code block again after 1 seconds
//                }
//            }
//        }
//    }


    private fun showConfigReadError() {
        MaterialDialog(this).show {
            title(R.string.config_error_title)
            message(R.string.config_error_dialog)
            positiveButton(android.R.string.ok)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    initUi()
                } else {
                    finish()
                }
                return
            }

            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onNavigationItemSelected(m: MenuItem): Boolean {
        when (m.itemId) {
            R.id.botNav_home -> {
                loadFragment(mMainFragment)
                return true
            }
            R.id.botNav_profiles -> {
                loadFragment(mProfilesFragment)
                return true
            }
            // TODO: Chancge id of schedules menu item
            R.id.botNav_settings -> {
                loadFragment(mSchedulesFragment)
                return true
            }
        }

        return false
    }

    private fun loadFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    /**
     * Function for ACCD status card OnClick in DashboardFragment
     */
    fun accdOnClick(view: View) {
        if (dash_accdButtons_linLay.visibility == View.GONE) {
            dash_accdButtons_linLay.visibility = View.VISIBLE
            dash_title_accdStatus_textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_arrow_drop_up_24px, 0)
        } else {
            dash_accdButtons_linLay.visibility = View.GONE
            dash_title_accdStatus_textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_baseline_arrow_drop_down_24px, 0)
        }
    }

    /**
     * Function for Status Card Settings OnClick (Configuration)
     */
    fun batteryConfigOnClick(view: View) {
        Intent(view.context, AccConfigEditorActivity::class.java).also { intent ->
            startActivityForResult(intent, ACC_CONFIG_EDITOR_REQUEST)
        }
    }

    /**
     * Function for starting and stopping the ACCD
     */
    fun accdStartStopOnClick(view: View) {

        Toast.makeText(this, R.string.wait, Toast.LENGTH_LONG).show()

        doAsync {
            if(AccUtils.isAccdRunning())
                AccUtils.abcStopDaemon()
            else
                AccUtils.abcStartDaemon()
        }
    }

    /**
     * Function for restarting the ACCD
     */
    fun accdRestartDaemonOnClick(view: View) {
        Toast.makeText(this, R.string.wait, Toast.LENGTH_LONG).show()

        doAsync {
            AccUtils.abcRestartDaemon()
        }
    }

    /**
     * Function for launching the profile creation Activity
     */
    fun accProfilesFabOnClick(view: View) {
        Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
            intent.putExtra("title", this@MainActivity.getString(R.string.profile_creator))
            startActivityForResult(intent, ACC_PROFILE_CREATOR_REQUEST)
        }
    }

//    private fun initProfiles() {
//        val profileList = ProfileUtils.listProfiles(this, gson)
//
//        val currentProfile = mSharedPrefs.getString("PROFILE", null)
//
//        val layoutManager = GridLayoutManager(this, 3)
//        profilesAdapter = ProfilesViewAdapter(ArrayList(profileList.map { AccaProfile(it) }), currentProfile) { profile, longPress ->
//            if(longPress) {
//                MaterialDialog(this@MainActivity).show {
//                    listItems(R.array.profile_long_press_options) { _, index, _ ->
//                        when(index) {
//                            0 -> {
//                                Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
//                                    val dataBundle = Bundle()
//                                    dataBundle.putString("profileName", profile.profileName)
//
//                                    intent.putExtra("mAccConfig", ProfileUtils.readProfile(profile.profileName, this@MainActivity, gson))
//                                    intent.putExtra("data", dataBundle)
//                                    intent.putExtra("title", this@MainActivity.getString(R.string.profile_creator))
//                                    startActivityForResult(intent, ACC_PROFILE_EDITOR_REQUEST)
//                                }
//                            }
//                            1 -> {
//                                MaterialDialog(this@MainActivity)
//                                    .show {
//                                        title(R.string.profile_name)
//                                        message(R.string.dialog_profile_name_message)
//                                        input(prefill = profile.profileName) { _, charSequence ->
//                                            //profiles index
//                                            val profileList: MutableList<String> = ProfileUtils.listProfiles(this@MainActivity, gson).toMutableList()
//
//                                            if(profileList.contains(charSequence.toString())) {
//                                                //TODO input not valid
//                                                return@input
//                                            }
//
//                                            profileList.add(charSequence.toString())
//                                            profileList.remove(profile.profileName) //remove all profile name
//                                            ProfileUtils.writeProfiles(this@MainActivity, profileList, gson) //Update profiles file with new profile
//
//                                            //Saving profile
//                                            val f = File(context.filesDir, "${profile.profileName}.profile")
//                                            f.renameTo(File(context.filesDir, "$charSequence.profile"))
//
//                                            profile.profileName = charSequence.toString()
//                                            profilesAdapter?.notifyItemChanged(profile)
//                                        }
//                                        positiveButton(R.string.save)
//                                        negativeButton(android.R.string.cancel)
//                                    }
//                            }
//                            2 -> {
//                                val f = File(context.filesDir, "$profile.profile")
//                                f.delete()
//
//                                ProfileUtils.writeProfiles(
//                                    this@MainActivity,
//                                    ProfileUtils
//                                        .listProfiles(this@MainActivity, gson).filter { it != profile.profileName },
//                                    gson
//                                ) //update profile list without this element
//
//                                profilesAdapter?.remove(profile)
//                                if(profilesAdapter?.itemCount == 0) {
//                                    this@MainActivity.profiles_recyclerview.visibility = android.view.View.GONE
//                                    this@MainActivity.no_profiles_textview.visibility = android.view.View.VISIBLE
//                                }
//                            }
//                        }
//                    }
//                }
//            } else {
//                //apply profile
//                val profileConfig = ProfileUtils.readProfile(profile.profileName, this@MainActivity, gson)
//
//                doAsync {
//                    val res = profileConfig.updateAcc()
//
//                    ProfileUtils.saveCurrentProfile(profile.profileName, mSharedPrefs)
//
//                    if(!res.voltControlUpdateSuccessful) {
//                        uiThread {
//                            Toast.makeText(this@MainActivity, R.string.wrong_volt_file, Toast.LENGTH_LONG).show()
//                        }
//                    }
//                }
//
//                profilesAdapter?.selectedProfile = profile.profileName
//                profilesAdapter?.notifyDataSetChanged()
//            }
//        }
//
//        profiles_recyclerview.layoutManager = layoutManager
//        profiles_recyclerview.adapter = profilesAdapter
//
//        if(profileList.isNotEmpty()) {
//            profiles_recyclerview.visibility = android.view.View.VISIBLE
//            no_profiles_textview.visibility = android.view.View.GONE
//        } else {
//            profiles_recyclerview.visibility = android.view.View.GONE
//            no_profiles_textview.visibility = android.view.View.VISIBLE
//        }
//    }

    private fun initUi() {

        // Set Bottom Navigation Bar Item Selected Listener
        var mNavbar = botNav_main
        mNavbar.setOnNavigationItemSelectedListener(this)

        // Read ACC configuration
        try {
            this.mAccConfig = AccUtils.readConfig()
        } catch (ex: Exception) {
            ex.printStackTrace()
            showConfigReadError()
            this.mAccConfig = AccUtils.defaultConfig //if mAccConfig is null I use default mAccConfig values.
        }

        // Load in dashboard fragment
        loadFragment(mMainFragment)

        // TODO: Move profiles to a new AccaProfile Activity
        //Profiles
//        initProfiles()

        //Rest of the UI
        // TODO: Reposition the onClick handler so that it uses callbacks to the MainActivity
//        create_acc_profile.setOnClickListener {
//            Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
//                intent.putExtra("title", this@MainActivity.getString(R.string.profile_creator))
//                startActivityForResult(intent, ACC_PROFILE_CREATOR_REQUEST)
//            }
//        }

//        edit_charging_limit_once_bt.setOnClickListener {
//            val dialog = MaterialDialog(this).show {
//                title(R.string.edit_charging_limit_once)
//                message(R.string.edit_charging_limit_once_dialog_msg)
//                customView(R.layout.edit_charging_limit_once_dialog)
//                positiveButton(R.string.apply) {
//                    AccUtils.setChargingLimitForOneCharge(getCustomView().findViewById<NumberPicker>(R.id.charging_limit).value)
//                    Toast.makeText(this@MainActivity, R.string.done, Toast.LENGTH_LONG).show()
//                }
//                negativeButton(android.R.string.cancel)
//            }
//
//            val picker = dialog.getCustomView().findViewById<NumberPicker>(R.id.charging_limit)
//            picker.maxValue = 100
//            picker.minValue = mAccConfig.capacity.pauseCapacity
//            picker.value = 100
//        }
//
//        reset_stats_on_unplugged_switch.setOnCheckedChangeListener { _, isChecked ->
//            mAccConfig.resetUnplugged = isChecked
//            AccUtils.updateResetUnplugged(isChecked)
//
//            //If I manually modify the mAccConfig I have to set current profile to null (custom profile)
//            ProfileUtils.saveCurrentProfile(null, mSharedPrefs)
//        }
//
//        reset_stats_on_unplugged_switch.isChecked = mAccConfig.resetUnplugged
//        reset_battery_stats.setOnClickListener {
//            AccUtils.resetBatteryStats()
//        }

        // TODO: Integrate schedules into another fragment
//        val schedules = ArrayList(AccUtils.listAllSchedules())
//        if(schedules.isEmpty()) {
//            no_schedules_jobs_textview.visibility = View.VISIBLE
//            scheduled_jobs_recyclerview.visibility = View.GONE
//        }

        // TODO: Move the recyclerview stuff into the respective ViewModels
//        scheduleAdapter = ScheduleRecyclerViewAdapter(schedules) { schedule, delete ->
//            if(delete) {
//                deleteSchedule(schedule)
//            } else {
//                MaterialDialog(this).show {
//                    title(R.string.schedule_job)
//                    message(R.string.edit_scheduled_command)
//                    input(prefill = schedule.command, inputType = TYPE_TEXT_FLAG_NO_SUGGESTIONS, allowEmpty = false) { _, charSequence ->
//                        schedule.command =  charSequence.toString()
//                        AccUtils.schedule(schedule.executeOnce, schedule.hour, schedule.minute, charSequence.toString())
//                    }
//                    positiveButton(R.string.save)
//                    negativeButton(android.R.string.cancel)
//                    neutralButton(R.string.delete) {
//                        deleteSchedule(schedule)
//                    }
//                }
//            }
//        }


//        val layoutManager = LinearLayoutManager(this)
//        scheduled_jobs_recyclerview.layoutManager = layoutManager
//        scheduled_jobs_recyclerview.adapter = scheduleAdapter

        // TODO: Move schedule onClicks to the new Schedule fragment
//        create_schedule.setOnClickListener {
//            val dialog = MaterialDialog(this@MainActivity).show {
//                customView(R.layout.schedule_dialog)
//                positiveButton(R.string.save) { dialog ->
//                    val view = dialog.getCustomView()
//                    val spinner = view.findViewById<Spinner>(R.id.profile_selector)
//                    val executeOnceCheckBox = view.findViewById<CheckBox>(R.id.schedule_recurrency)
//                    val timePicker = view.findViewById<TimePicker>(R.id.time_picker)
//                    val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.hour else timePicker.currentHour
//                    val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.minute else timePicker.currentMinute
//
//                    if(spinner.selectedItemId == 0.toLong()) {
//                        Intent(this@MainActivity, AccConfigEditorActivity::class.java).also { intent ->
//                            val dataBundle = Bundle()
//                            dataBundle.putInt("hour", hour)
//                            dataBundle.putInt("minute", minute)
//                            dataBundle.putBoolean("executeOnce", executeOnceCheckBox.isChecked)
//
//                            intent.putExtra("data", dataBundle)
//                            intent.putExtra("title", this@MainActivity.getString(R.string.schedule_creator))
//                            startActivityForResult(intent, ACC_PROFILE_SCHEDULER_REQUEST)
//                        }
//                    } else {
//                        val profile = spinner.selectedItem as String
//                        val configProfile = ProfileUtils.readProfile(profile, this@MainActivity, gson)
//
//                        addSchedule(Schedule("$hour$minute", executeOnceCheckBox.isChecked, hour, minute, configProfile.getCommands().joinToString(separator = "; ")))
//
//                        AccUtils.schedule(
//                            executeOnceCheckBox.isChecked,
//                            hour,
//                            minute,
//                            configProfile.getCommands()
//                        )
//                    }
//                }
//                negativeButton(android.R.string.cancel)
//            }

//            val profiles = ArrayList<String>()
//            profiles.add(getString(R.string.new_config))
//            profiles.addAll(ProfileUtils.listProfiles(this, gson))
//            val view = dialog.getCustomView()
//            val spinner = view.findViewById<Spinner>(R.id.profile_selector)
//            val adapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, profiles)
//
//            view.findViewById<TimePicker>(R.id.time_picker).setIs24HourView(true)
//
//            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//            spinner.adapter = adapter;
//        }
    }

    private fun checkPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST)
            return false
        }
        return true
    }

    private fun showRebootDialog() {
        val dialog = MaterialDialog(this)
            .show {
                title(R.string.reboot_dialog_title)
                message(R.string.reboot_dialog_description)
                positiveButton(R.string.reboot) {
                    Shell.su("reboot").exec()
                }
                negativeButton(android.R.string.cancel) {
                    finish()
                }
                cancelOnTouchOutside(false)
            }

        dialog.setOnKeyListener { _, keyCode, _ ->
            if(keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss()
                finish()
                false
            } else true
        }
    }

    private fun checkAccInstalled(): Boolean {
        if(!AccUtils.isAccInstalled()) {
            if(Shell.su("test -f /dev/acc/installed").exec().code == 0) {
                showRebootDialog()
                return false
            }

            val dialog = MaterialDialog(this).show {
                title(R.string.installing_acc)
                progress(R.string.wait)
                cancelOnTouchOutside(false)
            }

            dialog.setOnKeyListener { _, keyCode, _ ->
                keyCode == KeyEvent.KEYCODE_BACK
            }

            doAsync {
                val res = AccUtils.installAccModule(this@MainActivity)?.isSuccess == true
                uiThread {
                    dialog.cancel()

                    if(!res) {
                        val failureDialog = MaterialDialog(this@MainActivity)
                            .show {
                                title(R.string.installation_failed_title)
                                message(R.string.installation_failed)
                                positiveButton(R.string.retry) {
                                    if(checkAccInstalled() && checkPermissions())
                                        initUi()
                                }
                                negativeButton {
                                    finish()
                                }
                                cancelOnTouchOutside(false)
                            }

                        failureDialog.setOnKeyListener { _, keyCode, _ ->
                            if(keyCode == KeyEvent.KEYCODE_BACK) {
                                dialog.dismiss()
                                finish()
                                false
                            } else true
                        }
                    } else {
                        showRebootDialog()
                    }
                }
            }
            return false
        }

        return true
    }

    // TODO: If necessary, imput this into the ViewModel and bind it.

//    override fun onSaveInstanceState(outState: Bundle?) {
//        outState?.putParcelable("batteryInfo", batteryInfo)
//        outState?.putBoolean("daemonRunning", isDaemonRunning)
//
//        super.onSaveInstanceState(outState)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Assign ViewModel
        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val appUpdater = AppUpdater(this)
            .setDisplay(Display.NOTIFICATION)
            .setUpdateFrom(UpdateFrom.GITHUB)
            .setGitHubUserAndRepo("MatteCarra", "AccA")
            .setIcon(R.drawable.ic_notification)
        appUpdater.start()

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        if(!Shell.rootAccess()) {
            val dialog = MaterialDialog(this).show {
                title(R.string.tile_acc_no_root)
                message(R.string.no_root_message)
                positiveButton(android.R.string.ok) {
                    finish()
                }
                cancelOnTouchOutside(false)
            }

            dialog.setOnKeyListener { _, keyCode, _ ->
                if(keyCode == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss()
                    finish()
                    false
                } else true
            }
            return
        }

        if(checkAccInstalled() && checkPermissions()) {
            initUi()

            // TODO: Saved instance state
//            savedInstanceState?.let { bundle ->
//                updateAccdStatus(bundle.getBoolean("daemonRunning", false))
//                bundle.getParcelable<BatteryInfo>("batteryInfo")?.let {
//                    setBatteryInfo(it)
//                }
//            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ACC_CONFIG_EDITOR_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if(data?.getBooleanExtra("hasChanges", false) == true) {
                    mAccConfig = data.getParcelableExtra("config")
                    doAsync {
                        val res = mAccConfig.updateAcc()


                        // TODO: Implement this logic after profiles have been implemented. Use callbacks for that fragment.
//                        //If I manually modify the mAccConfig I have to set current profile to null (custom profile)
//                        ProfileUtils.saveCurrentProfile(null, mSharedPrefs)
//                        profilesAdapter?.let { adapter ->
//                            uiThread {
//                                if(!res.voltControlUpdateSuccessful) {
//                                    Toast.makeText(this@MainActivity, R.string.wrong_volt_file, Toast.LENGTH_LONG).show()
//                                }
//
//                                adapter.selectedProfile = null
//                                adapter.notifyDataSetChanged()
//                            }
//                        }
                    }
                }
            }
        }
        else if(requestCode == ACC_PROFILE_CREATOR_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    val config: AccConfig = data.getParcelableExtra("config")
                    val fileNameRegex = """^[^\\/:*?"<>|]+${'$'}""".toRegex()
                    MaterialDialog(this)
                        .show {
                            title(R.string.profile_name)
                            message(R.string.dialog_profile_name_message)
                            input(waitForPositiveButton = false) { dialog, charSequence ->
                                val inputField = dialog.getInputField()
                                val isValid = fileNameRegex.matches(charSequence)

                                inputField.error = if (isValid) null else getString(R.string.invalid_chars)
                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
                            }
                            positiveButton(R.string.save) { dialog ->
                                val profileName = dialog.getInputField().text.toString()

                                // Add Profile to Database via ViewModel function
                                val profile = AccaProfile(
                                    0,
                                    profileName,
                                    mAccConfig
//                                    config.capacity.coolDownCapacity,
//                                    config.capacity.pauseCapacity,
//                                    config.capacity.pauseCapacity,
//                                    config.capacity.shutdownCapacity,
//                                    config.chargingSwitch,
//                                    config.cooldown!!.charge,
//                                    config.cooldown!!.pause,
//                                    config.onBoot,
//                                    config.onBootExit,
//                                    config.onPlugged,
//                                    config.resetUnplugged,
//                                    config.temp.coolDownTemp,
//                                    config.temp.pauseChargingTemp,
//                                    config.temp.waitSeconds,
//                                    config.voltControl.voltFile,
//                                    config.voltControl.voltMax
                                )

                                mViewModel.insertProfile(profile)

//                                //profiles index
//                                val profileList = ProfileUtils.listProfiles(this@MainActivity, gson).toMutableList()
//
//                                if(!profileList.contains(input)) {
//                                    profileList.add(input)
//                                    ProfileUtils.writeProfiles(this@MainActivity, profileList, gson) //Update profiles file with new profile
//                                }
//
//                                //Saving profile
//                                val f = File(context.filesDir, "$input.profile")
//                                val json = gson.toJson(mAccConfig)
//                                f.writeText(json)
//
//                                if(profilesAdapter?.itemCount == 0) {
//                                    this@MainActivity.profiles_recyclerview.visibility = android.view.View.VISIBLE
//                                    this@MainActivity.no_profiles_textview.visibility = android.view.View.GONE
//                                }
//
//                                profilesAdapter?.add(AccaProfile(input))

                            }
                            negativeButton(android.R.string.cancel)
                        }
                }
            }
        }
//        } else if(requestCode == ACC_PROFILE_EDITOR_REQUEST) {
//            if (resultCode == Activity.RESULT_OK) {
//                if(data?.getBooleanExtra("hasChanges", false) == true && data.hasExtra("data")) {
//                    val mAccConfig: AccConfig = data.getParcelableExtra("mAccConfig")
//                    val profileName = data.getBundleExtra("data").getString("profileName")
//
//                    //Saving profile
//                    val f = File(this@MainActivity.filesDir, "$profileName.profile")
//                    val json = gson.toJson(mAccConfig)
//                    f.writeText(json)
//                }
//            }
//        } else if(requestCode == ACC_PROFILE_SCHEDULER_REQUEST && resultCode == Activity.RESULT_OK) {
//            if(data?.hasExtra("data") == true) {
//                val dataBundle = data.getBundleExtra("data")
//
//                val hour = dataBundle.getInt("hour")
//                val minute = dataBundle.getInt("minute")
//                val executeOnce = dataBundle.getBoolean("executeOnce")
//                val commands = data.getParcelableExtra<AccConfig>("mAccConfig").getCommands()
//
//                addSchedule(Schedule("${String.format("%02d", hour)}${String.format("%02d", minute)}", executeOnce, hour, minute, commands.joinToString(separator = "; ")))
//
//                AccUtils.schedule(
//                    executeOnce,
//                    hour,
//                    minute,
//                    commands
//                )
//            }
//        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the main_activity_menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main_activity_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        when(item.itemId) {
            R.id.actions_logs -> {
                startActivity(Intent(this, LogViewerActivity::class.java))
            }
        }

        return super.onOptionsItemSelected(item)
    }

//    override fun onResume() {
//        handler.post(updateUIRunnable) // Start the initial runnable task by posting through the handler
//
//        super.onResume()
//    }
//
//    override fun onPause() {
//        handler.removeCallbacks(updateUIRunnable)
//
//        super.onPause()
//    }
}