package mattecarra.accapp.acc

import android.content.Context
import android.os.Environment
import com.topjohnwu.superuser.Shell
import mattecarra.accapp.R
import mattecarra.accapp.adapters.Schedule
import mattecarra.accapp.models.AccConfig
import mattecarra.accapp.models.BatteryInfo
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL


interface AccInterface {
    val defaultConfig: AccConfig

    fun readConfig(): AccConfig

    fun listVoltageSupportedControlFiles(): List<String>

    fun resetBatteryStats(): Boolean

    fun getBatteryInfo(): BatteryInfo

    fun isBatteryCharging(): Boolean

    fun isAccdRunning(): Boolean

    fun abcStartDaemon(): Boolean

    fun abcRestartDaemon(): Boolean

    fun abcStopDaemon(): Boolean

    fun deleteSchedule(once: Boolean, name: String): Boolean

    fun schedule(once: Boolean, hour: Int, minute: Int, commands: List<String>): Boolean

    fun schedule(once: Boolean, hour: Int, minute: Int, commands: String): Boolean

    fun listSchedules(once: Boolean): List<Schedule>

    fun listAllSchedules(): List<Schedule>

    fun listChargingSwitches(): List<String>

    fun testChargingSwitch(chargingSwitch: String? = null): Int

    fun getCurrentChargingSwitch(config: String): String?

    fun setChargingLimitForOneCharge(limit: Int): Boolean

    fun updateAccConfig(accConfig: AccConfig): ConfigUpdateResult

    //reset unplugged command
    fun updateResetUnplugged(resetUnplugged: Boolean): Boolean

    /**
     * Updates the cool down charge and pause durations.
     * @param charge seconds to charge for during the cool down phase.
     * @param pause seconds to pause for during the cool down phase.
     * @return boolean if the command was successful.
     */
    fun updateAccCoolDown(charge: Int?, pause: Int?) : Boolean

    /**
     * Updates the capacity related settings of ACC.
     * @param shutdown shutdown the device at the specified percentage.
     * @param coolDown starts the cool down phase at the specified percentage.
     * @param resume allows charging starting from the specified capacity.
     * @param pause pauses charging at the specified capacity.
     * @return boolean if the command was successful.
     */
    fun updateAccCapacity(shutdown: Int, coolDown: Int, resume: Int, pause: Int) : Boolean

    /**
     * Updates the temperature related configuration in ACC.
     * @param coolDownTemperature starts cool down phase at the specified temperature.
     * @param pauseTemperature pauses charging at the specified temperature.
     * @param wait seconds to wait until charging is resumed.
     * @return the boolean result of the command's execution.
     */
    fun updateAccTemperature(coolDownTemperature: Int, temperatureMax: Int, wait: Int) : Boolean

    /**
     * Updates the voltage related configuration in ACC.
     * @param voltFile path to the voltage file on the device.
     * @param voltMax maximum voltage the phone should charge at.
     * @return the boolean result of the command's execution.
     */
    fun updateAccVoltControl(voltFile: String?, voltMax: Int?) : Boolean

    /**
     * Updates the on boot exit (boolean) configuration in ACC.
     * @param enabled boolean: if OnBootExit should be enabled.
     * @return the boolean result of the command's execution.
     */
    fun updateAccOnBootExit(enabled: Boolean) : Boolean

    /**
     * Updates the OnBoot command configuration in ACC.
     * @param command the command to be run after the device starts (daemon starts).
     * @return the boolean result of the command's execution.
     */
    fun updateAccOnBoot(command: String?) : Boolean

    /**
     * Updates the OnPlugged configuration in ACC.
     * @param command the command to be run when the device is plugged in.
     * @return the boolean result of the command's execution.
     */
    fun updateAccOnPlugged(command: String?) : Boolean
    fun updateAccChargingSwitch(switch: String?) : Boolean
}

object Acc {
    private val VERSION_REGEXP = """^\s*versionCode=([\d*]+)""".toRegex(RegexOption.MULTILINE)

    private const val latestVersion = 201905111

    private fun getVersionPackageName(v: Int): Int {
        return when {
            v >= 201903071 -> 201903071
            else           -> 201905111
        }
    }

    val instance: AccInterface by lazy {
        val constructor = try {
            val configFile =
                if(File(Environment.getExternalStorageDirectory(), "acc/acc.conf").exists())
                    File(Environment.getExternalStorageDirectory(), "acc/acc.conf")
                else
                    File(Environment.getExternalStorageDirectory(), "acc/config.txt")

            val config = configFile.readText()

            val version = VERSION_REGEXP.find(config)?.destructured?.component1()?.toIntOrNull() ?: latestVersion

            val aClass = Class.forName("mattecarra.accapp.acc.v${getVersionPackageName(version)}.AccHandler")
            aClass.getDeclaredConstructor()
        } catch (ex: Exception) {
            val aClass = Class.forName("mattecarra.accapp.acc.v$latestVersion.AccHandler")
            aClass.getDeclaredConstructor()
        }

        constructor.newInstance() as AccInterface
    }

    fun isBundledAccInstalled(context: Context): Boolean {
        return File(context.filesDir, "acc/acc").exists()
    }

    fun isAccInstalled(): Boolean {
        return Shell.su("which acc > /dev/null").exec().isSuccess
    }

    fun installBundledAccModule(context: Context): Shell.Result? {
        return try {
            context.resources.openRawResource(R.raw.acc_bundle).use { out ->
                val filesDirPath = context.filesDir.absolutePath
                val bundle = File(context.filesDir, "acc_bundle.tar.gz")
                out.copyTo(FileOutputStream(bundle))
                Shell.su("set -e", "tar -xf ${bundle.absolutePath} -C $filesDirPath && sh $filesDirPath/*/install-current.sh && rm -rf $filesDirPath/acc*/").exec()
            }
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            null
        }
    }

    fun installAccModule(context: Context): Shell.Result? {
        try {
            val scriptFile = File(context.filesDir, "install-latest.sh")
            val path = scriptFile.absolutePath

            BufferedInputStream(URL("https://raw.githubusercontent.com/VR-25/acc/master/install-latest.sh").openStream())
                .use { inStream ->
                    FileOutputStream(scriptFile)
                        .use {
                            val buf = ByteArray(1024)
                            var bytesRead = inStream.read(buf, 0, 1024)

                            while (bytesRead != -1) {
                                it.write(buf, 0, bytesRead)
                                bytesRead = inStream.read(buf, 0, 1024)
                            }
                        }
                }

            return Shell.su("sh $path").exec()
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            return null
        }
    }
}
