package io.github.iokkai.ocularnode.server

import android.content.Context
import io.github.iokkai.ocularnode.R

/**
 * 負責構建與快取 Web Dashboard 靜態 HTML/JS/CSS 儀表板頁面。
 */
class WebDashboardProvider(private val context: Context) {

    @Volatile
    private var cachedDashboardHtml: String? = null

    fun getWebDashboardHtml(): String {
        return cachedDashboardHtml ?: synchronized(this) {
            cachedDashboardHtml ?: buildWebDashboardHtml().also { cachedDashboardHtml = it }
        }
    }

    private fun buildWebDashboardHtml(): String {
        val sPageTitle = context.getString(R.string.web_page_title)
        val sLoading = context.getString(R.string.web_loading)
        val sConnected = context.getString(R.string.web_connected)
        val sSettingsBtn = context.getString(R.string.web_settings_btn)
        val sVideoAlt = context.getString(R.string.web_video_alt)
        val sBtnRefresh = context.getString(R.string.web_btn_refresh)
        val sOverviewTitle = context.getString(R.string.web_overview_title)
        val sStatViewers = context.getString(R.string.web_stat_viewers)
        val sStatViewersSuffix = context.getString(R.string.web_stat_viewers_suffix)
        val sStatBattery = context.getString(R.string.web_stat_battery)
        val sStatFps = context.getString(R.string.web_stat_fps)
        val sStatNightVision = context.getString(R.string.web_stat_night_vision)
        val sModeTitle = context.getString(R.string.web_mode_title)
        val sModeMonitor = context.getString(R.string.web_mode_monitor)
        val sModeDetection = context.getString(R.string.web_mode_detection)
        val sControlsTitle = context.getString(R.string.web_controls_title)
        val sCtrlSwitchLens = context.getString(R.string.web_ctrl_switch_lens)
        val sCtrlTorch = context.getString(R.string.web_ctrl_torch)
        val sCtrlSnapshot = context.getString(R.string.web_ctrl_snapshot)
        val sCtrlSiren = context.getString(R.string.web_ctrl_siren)
        val sAudioTitle = context.getString(R.string.web_audio_title)
        val sAudioListen = context.getString(R.string.web_audio_listen)
        val sAudioMute = context.getString(R.string.web_audio_mute)
        val sNightTitle = context.getString(R.string.web_night_title)
        val sNightOff = context.getString(R.string.web_night_off)
        val sNightOn = context.getString(R.string.web_night_on)
        val sNightAuto = context.getString(R.string.web_night_auto)
        val sStorageTitle = context.getString(R.string.web_storage_title)
        val sStorageAvailable = context.getString(R.string.web_storage_available)

        val sModalTitle = context.getString(R.string.web_modal_title)
        val sCfgDeviceName = context.getString(R.string.web_cfg_device_name)
        val sCfgDeviceNamePh = context.getString(R.string.web_cfg_device_name_ph)
        val sCfgCameraQuality = context.getString(R.string.web_cfg_camera_quality)
        val sCfgResolution = context.getString(R.string.web_cfg_resolution)
        val sCfgJpegQuality = context.getString(R.string.web_cfg_jpeg_quality)
        val sCfgQualityHigh = context.getString(R.string.web_cfg_quality_high)
        val sCfgQualityBalanced = context.getString(R.string.web_cfg_quality_balanced)
        val sCfgQualitySmooth = context.getString(R.string.web_cfg_quality_smooth)
        val sCfgQualityLow = context.getString(R.string.web_cfg_quality_low)
        val sCfgQualityVeryLow = context.getString(R.string.web_cfg_quality_very_low)
        val sCfgQualityMinimal = context.getString(R.string.web_cfg_quality_minimal)

        val sCfgMotionAi = context.getString(R.string.web_cfg_motion_ai)
        val sCfgSensitivity = context.getString(R.string.web_cfg_sensitivity)
        val sCfgCooldown = context.getString(R.string.web_cfg_cooldown)
        val sCfgMotionSched = context.getString(R.string.web_cfg_motion_sched)
        val sCfgStartTime = context.getString(R.string.web_cfg_start_time)
        val sCfgEndTime = context.getString(R.string.web_cfg_end_time)
        val sCfgNotifCategories = context.getString(R.string.web_cfg_notif_categories)
        val sCfgCatHuman = context.getString(R.string.web_cfg_cat_human)
        val sCfgCatPet = context.getString(R.string.web_cfg_cat_pet)
        val sCfgCatVehicle = context.getString(R.string.web_cfg_cat_vehicle)
        val sCfgCatHousehold = context.getString(R.string.web_cfg_cat_household)
        val sCfgCatEnvironment = context.getString(R.string.web_cfg_cat_environment)
        val sCfgCatOther = context.getString(R.string.web_cfg_cat_other)

        val sCfgRecordingStorage = context.getString(R.string.web_cfg_recording_storage)
        val sCfgEventRecording = context.getString(R.string.web_cfg_event_recording)
        val sCfgMaxStorage = context.getString(R.string.web_cfg_max_storage)
        val sCfgAutoRecCategories = context.getString(R.string.web_cfg_auto_rec_categories)
        val sCfgProtectionTg = context.getString(R.string.web_cfg_protection_tg)
        val sCfgPowerCut = context.getString(R.string.web_cfg_power_cut)
        val sCfgSysLog = context.getString(R.string.web_cfg_sys_log)
        val sCfgNotifSched = context.getString(R.string.web_cfg_notif_sched)
        val sCfgNotifStartTime = context.getString(R.string.web_cfg_notif_start_time)
        val sCfgNotifEndTime = context.getString(R.string.web_cfg_notif_end_time)
        val sCfgCancel = context.getString(R.string.web_cfg_cancel)
        val sCfgSaveApply = context.getString(R.string.web_cfg_save_apply)

        val sPerfTitle = context.getString(R.string.web_perf_title)
        val sPerfActive = context.getString(R.string.web_perf_active)
        val sPerfToggle = context.getString(R.string.web_perf_toggle)
        val sPerfCpu = context.getString(R.string.web_perf_cpu)
        val sPerfMem = context.getString(R.string.web_perf_mem)
        val sPerfPing = context.getString(R.string.web_perf_ping)
        val sPerfRamLegend = context.getString(R.string.web_perf_ram_legend)

        val sEventsTitle = context.getString(R.string.web_events_title)
        val sEventsRefresh = context.getString(R.string.web_events_refresh)
        val sEventsClearAll = context.getString(R.string.web_events_clear_all)
        val sEventsLoading = context.getString(R.string.web_events_loading)
        val sEventsEmpty = context.getString(R.string.web_events_empty)
        val sEventsMotionPrefix = context.getString(R.string.web_events_motion_prefix)
        val sEventsPhoto = context.getString(R.string.web_events_photo)
        val sEventsVideo = context.getString(R.string.web_events_video)

        val sJsConfigLoadFail = context.getString(R.string.web_js_config_load_fail)
        val sJsConfigSaved = context.getString(R.string.web_js_config_saved)
        val sJsConfigSaveFail = context.getString(R.string.web_js_config_save_fail)
        val sJsConfigSendFail = context.getString(R.string.web_js_config_send_fail)
        val sJsSnapshotMode = context.getString(R.string.web_js_snapshot_mode)
        val sJsHotBattery = context.getString(R.string.web_js_hot_battery)
        val sJsNightActive = context.getString(R.string.web_js_night_active)
        val sJsNightNormal = context.getString(R.string.web_js_night_normal)
        val sJsDelConfirm = context.getString(R.string.web_js_del_confirm)
        val sJsDelFail = context.getString(R.string.web_js_del_fail)
        val sJsClearConfirm = context.getString(R.string.web_js_clear_confirm)
        val sJsClearFail = context.getString(R.string.web_js_clear_fail)
        val sJsCmdFail = context.getString(R.string.web_js_cmd_fail)
        val sJsAudioUnsupported = context.getString(R.string.web_js_audio_unsupported)
        val sJsAudioFail = context.getString(R.string.web_js_audio_fail)

        return """<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$sPageTitle</title>
    <style>
        :root {
            --primary: #6750A4;
            --primary-bg: #EADDFF;
            --bg: #0F172A;
            --card-bg: #1E293B;
            --text: #F8FAFC;
            --subtext: #94A3B8;
            --accent-red: #EF4444;
            --accent-green: #22C55E;
            --accent-blue: #3B82F6;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        body { background-color: var(--bg); color: var(--text); padding: 16px; display: flex; flex-direction: column; align-items: center; min-height: 100vh; }
        header { width: 100%; max-width: 900px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px; }
        h1 { font-size: 1.4rem; color: #E2E8F0; display: flex; align-items: center; gap: 8px; }
        .badge { background: var(--primary); color: white; padding: 4px 10px; border-radius: 20px; font-size: 0.8rem; font-weight: bold; }
        .live-tag { background: var(--accent-green); color: white; padding: 4px 12px; border-radius: 20px; font-size: 0.85rem; font-weight: bold; }
        
        .main-container { width: 100%; max-width: 900px; display: grid; grid-template-columns: 1fr; gap: 16px; }
        @media (min-width: 768px) { .main-container { grid-template-columns: 3fr 2fr; } }
        
        .video-card { background: #000; border-radius: 16px; overflow: hidden; position: relative; border: 1px solid #334155; display: flex; justify-content: center; align-items: center; min-height: 360px; }
        .video-feed { width: 100%; height: auto; max-height: 520px; object-fit: contain; display: block; }
        
        .panel-card { background: var(--card-bg); border-radius: 16px; padding: 14px; border: 1px solid #334155; display: flex; flex-direction: column; gap: 10px; }
        .panel-title { font-size: 0.95rem; font-weight: bold; border-bottom: 1px solid #334155; padding-bottom: 6px; margin-bottom: 2px; color: #CBD5E1; display: flex; justify-content: space-between; align-items: center; }
        
        .stat-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
        .stat-box { background: #0F172A; padding: 6px 10px; border-radius: 8px; border: 1px solid #334155; }
        .stat-label { font-size: 0.7rem; color: var(--subtext); margin-bottom: 0px; }
        .stat-val { font-size: 0.95rem; font-weight: bold; color: #F1F5F9; }
        
        .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-top: 2px; }
        .btn { background: #334155; color: white; border: none; padding: 8px 10px; border-radius: 8px; font-size: 0.85rem; font-weight: bold; cursor: pointer; transition: all 0.2s; display: inline-flex; align-items: center; justify-content: center; gap: 4px; text-decoration: none; }
        .btn:hover { background: #475569; }
        .btn-primary { background: var(--primary); }
        .btn-primary:hover { background: #7E67C1; }
        .btn-danger { background: var(--accent-red); }
        .btn-danger:hover { background: #DC2626; }
        .btn-success { background: var(--accent-green); }
        .btn-success:hover { background: #16A34A; }
        
        canvas { width: 100%; height: 160px; display: block; border-radius: 8px; background: #0F172A; border: 1px solid #334155; }

        /* Modal styling */
        .modal-overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.75); display: none; justify-content: center; align-items: center; z-index: 1000; padding: 16px; backdrop-filter: blur(4px); }
        .modal-content { background: var(--card-bg); border-radius: 16px; border: 1px solid #475569; width: 100%; max-width: 640px; max-height: 90vh; overflow-y: auto; padding: 20px; display: flex; flex-direction: column; gap: 16px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5); }
        .modal-header { display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #334155; padding-bottom: 12px; }
        .modal-header h2 { font-size: 1.2rem; color: #F1F5F9; }
        .form-group { display: flex; flex-direction: column; gap: 6px; }
        .form-label { font-size: 0.85rem; color: #CBD5E1; font-weight: bold; }
        .form-control { background: #0F172A; border: 1px solid #334155; color: white; padding: 8px 12px; border-radius: 8px; font-size: 0.9rem; }
        .checkbox-group { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; background: #0F172A; padding: 10px; border-radius: 8px; border: 1px solid #334155; }
        .checkbox-item { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; color: #E2E8F0; }
        .checkbox-item input { width: 16px; height: 16px; accent-color: var(--primary); }
    </style>
</head>
<body>
    <header>
        <h1><span class="badge">OcularNode</span> <span id="dev-name">$sLoading</span></h1>
        <div style="display: flex; gap: 8px; align-items: center;">
            <span class="live-tag" id="stream-status-tag">$sConnected</span>
            <span style="font-size: 0.85rem; color: var(--subtext);" id="fps-val">-- FPS</span>
            <button class="btn btn-primary" style="margin-left: 8px;" onclick="openConfigModal()">$sSettingsBtn</button>
        </div>
    </header>

    <div class="main-container">
        <!-- Video Stream Player -->
        <div class="video-card">
            <img id="stream" src="/mjpeg" class="video-feed" alt="$sVideoAlt" onerror="onStreamError()">
            <div style="position: absolute; top: 12px; left: 12px; display: flex; gap: 6px;">
                <span style="background: rgba(15,23,42,0.8); color: white; padding: 2px 8px; border-radius: 6px; font-size: 0.75rem; border: 1px solid #334155;" id="res-badge">720p (Quality 60%)</span>
                <span style="background: rgba(15,23,42,0.8); color: white; padding: 2px 8px; border-radius: 6px; font-size: 0.75rem; border: 1px solid #334155;" id="zoom-badge">1.0x</span>
            </div>
            <div style="position: absolute; bottom: 12px; right: 12px; display: flex; gap: 6px; flex-wrap: wrap;">
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="reloadStream()">$sBtnRefresh</button>
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem; background: rgba(15,23,42,0.8);" onclick="rotateStreamServer()">↻</button>
            </div>
        </div>

        <!-- Controls Side Panel (Direct Real-time Commands) -->
        <div class="panel-card">
            <div class="panel-title">$sOverviewTitle</div>
            <div class="stat-grid">
                <div class="stat-box"><div class="stat-label">$sStatViewers</div><div class="stat-val" id="clients-val">-- $sStatViewersSuffix</div></div>
                <div class="stat-box"><div class="stat-label">$sStatBattery</div><div class="stat-val" id="battery-val">--</div></div>
                <div class="stat-box"><div class="stat-label">$sStatFps</div><div class="stat-val" id="stat-fps-val">-- FPS</div></div>
                <div class="stat-box"><div class="stat-label">$sStatNightVision</div><div class="stat-val" id="night-val">--</div></div>
            </div>

            <div class="panel-title" style="margin-top: 6px;">$sModeTitle</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 6px;">
                <button class="btn" id="btn-mode-monitor" onclick="sendCommand('mode', 'monitor')">$sModeMonitor</button>
                <button class="btn" id="btn-mode-detection" onclick="sendCommand('mode', 'detection')">$sModeDetection</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">$sControlsTitle</div>
            <div class="btn-grid">
                <button class="btn" onclick="sendCommand('camera', 'switch')">$sCtrlSwitchLens</button>
                <button class="btn" onclick="sendCommand('torch', 'toggle')">$sCtrlTorch</button>
                <button class="btn" onclick="takeSnapshot()">$sCtrlSnapshot</button>
                <button class="btn btn-danger" onclick="sendCommand('alarm', 'trigger')">$sCtrlSiren</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">$sAudioTitle</div>
            <button class="btn" id="btn-audio-listen" onclick="toggleAudioListen()">$sAudioListen</button>

            <div class="panel-title" style="margin-top: 6px;">$sNightTitle</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 6px;">
                <button class="btn" id="btn-night-off" onclick="sendCommand('night_vision', 'off')">$sNightOff</button>
                <button class="btn" id="btn-night-on" onclick="sendCommand('night_vision', 'on')">$sNightOn</button>
                <button class="btn" id="btn-night-auto" onclick="sendCommand('night_vision', 'auto')">$sNightAuto</button>
            </div>

            <div class="panel-title" style="margin-top: 6px;">$sStorageTitle</div>
            <div class="stat-box"><div class="stat-label">$sStorageAvailable</div><div class="stat-val" id="storage-val">-- / --</div></div>
        </div>
    </div>

    <!-- System Settings Batch Modal Dialog -->
    <div id="config-modal" class="modal-overlay">
        <div class="modal-content">
            <div class="modal-header">
                <h2>$sModalTitle</h2>
                <button class="btn" style="padding: 4px 10px;" onclick="closeConfigModal()">✕</button>
            </div>

            <!-- Device Name -->
            <div class="form-group">
                <label class="form-label">$sCfgDeviceName</label>
                <input type="text" id="cfg-dev-name" class="form-control" placeholder="$sCfgDeviceNamePh">
            </div>

            <!-- Camera Config -->
            <div class="panel-title" style="margin-top: 4px;">$sCfgCameraQuality</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="form-label">$sCfgResolution</label>
                    <select id="cfg-resolution" class="form-control">
                        <option value="1080p">1080p</option>
                        <option value="960p">960p</option>
                        <option value="720p">720p</option>
                        <option value="480p">480p</option>
                        <option value="360p">360p</option>
                    </select>
                </div>
                <div class="form-group">
                    <label class="form-label">$sCfgJpegQuality</label>
                    <select id="cfg-quality" class="form-control">
                        <option value="90">$sCfgQualityHigh</option>
                        <option value="75">$sCfgQualityBalanced</option>
                        <option value="50">$sCfgQualitySmooth</option>
                        <option value="30">$sCfgQualityLow</option>
                        <option value="20">$sCfgQualityVeryLow</option>
                        <option value="15">$sCfgQualityMinimal</option>
                    </select>
                </div>
            </div>

            <!-- Motion Detection & AI Category Filters -->
            <div class="panel-title" style="margin-top: 4px;">$sCfgMotionAi</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="form-label">$sCfgSensitivity</label>
                    <input type="number" id="cfg-motion-sens" class="form-control" min="1" max="10" value="5">
                </div>
                <div class="form-group">
                    <label class="form-label">$sCfgCooldown</label>
                    <input type="number" id="cfg-motion-cooldown" class="form-control" min="5" max="300" value="10">
                </div>
            </div>

            <div style="margin-top: 8px; border-top: 1px dashed #334155; padding-top: 8px;">
                <label class="checkbox-item"><input type="checkbox" id="cfg-motion-sched-enable"> $sCfgMotionSched</label>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 6px;">
                    <div class="form-group">
                        <label class="form-label">$sCfgStartTime</label>
                        <input type="time" id="cfg-motion-sched-start" class="form-control" value="22:00">
                    </div>
                    <div class="form-group">
                        <label class="form-label">$sCfgEndTime</label>
                        <input type="time" id="cfg-motion-sched-end" class="form-control" value="06:00">
                    </div>
                </div>
            </div>

            <div class="form-label" style="margin-top: 10px;">$sCfgNotifCategories</div>
            <div class="checkbox-group">
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-HUMAN_AND_ACTIVITY" checked> $sCfgCatHuman</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-PET_AND_ANIMAL" checked> $sCfgCatPet</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-VEHICLE_AND_TRANSPORT" checked> $sCfgCatVehicle</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-HOUSEHOLD_ITEM" checked> $sCfgCatHousehold</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-ENVIRONMENT_AND_NATURE" checked> $sCfgCatEnvironment</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-cat-OTHER" checked> $sCfgCatOther</label>
            </div>

            <!-- Recording & Storage -->
            <div class="panel-title" style="margin-top: 4px;">$sCfgRecordingStorage</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <div class="form-group">
                    <label class="checkbox-item" style="margin-top: 24px;">
                        <input type="checkbox" id="cfg-event-recording" checked> $sCfgEventRecording
                    </label>
                </div>
                <div class="form-group">
                    <label class="form-label">$sCfgMaxStorage</label>
                    <input type="number" id="cfg-max-storage" class="form-control" min="1" max="100" step="0.5" value="10.0">
                </div>
            </div>

            <div class="form-label">$sCfgAutoRecCategories</div>
            <div class="checkbox-group">
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-HUMAN_AND_ACTIVITY" checked> $sCfgCatHuman</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-PET_AND_ANIMAL" checked> $sCfgCatPet</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-VEHICLE_AND_TRANSPORT" checked> $sCfgCatVehicle</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-HOUSEHOLD_ITEM" checked> $sCfgCatHousehold</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-ENVIRONMENT_AND_NATURE" checked> $sCfgCatEnvironment</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-rec-cat-OTHER" checked> $sCfgCatOther</label>
            </div>

            <!-- Protection & Telegram -->
            <div class="panel-title" style="margin-top: 4px;">$sCfgProtectionTg</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <label class="checkbox-item"><input type="checkbox" id="cfg-power-cut" checked> $sCfgPowerCut</label>
                <label class="checkbox-item"><input type="checkbox" id="cfg-sys-log" checked> $sCfgSysLog</label>
            </div>

            <!-- Security & PIN Protection -->
            <div class="panel-title" style="margin-top: 4px;">安全性與存取防護</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <label class="checkbox-item" style="margin-top: 8px;">
                    <input type="checkbox" id="cfg-auth-enable"> 啟用存取密碼保護
                </label>
                <div class="form-group">
                    <label class="form-label">自訂 PIN 碼 (留空或***表示不變更)</label>
                    <input type="password" id="cfg-auth-pin" class="form-control" placeholder="4-6 位數 PIN 碼">
                </div>
            </div>

            <div style="margin-top: 8px; border-top: 1px dashed #334155; padding-top: 8px;">
                <label class="checkbox-item"><input type="checkbox" id="cfg-notif-sched-enable"> $sCfgNotifSched</label>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 6px;">
                    <div class="form-group">
                        <label class="form-label">$sCfgNotifStartTime</label>
                        <input type="time" id="cfg-notif-sched-start" class="form-control" value="22:00">
                    </div>
                    <div class="form-group">
                        <label class="form-label">$sCfgNotifEndTime</label>
                        <input type="time" id="cfg-notif-sched-end" class="form-control" value="06:00">
                    </div>
                </div>
            </div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 6px;">
                <div class="form-group">
                    <label class="form-label">Telegram Bot Token</label>
                    <input type="text" id="cfg-tg-token" class="form-control" placeholder="bot123456:ABC...">
                </div>
                <div class="form-group">
                    <label class="form-label">Telegram Chat ID</label>
                    <input type="text" id="cfg-tg-chat" class="form-control" placeholder="-100123456789">
                </div>
            </div>

            <!-- Modal Actions -->
            <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; border-top: 1px solid #334155; padding-top: 12px;">
                <button class="btn" onclick="closeConfigModal()">$sCfgCancel</button>
                <button class="btn btn-primary" onclick="saveConfigBatch()">$sCfgSaveApply</button>
            </div>
        </div>
    </div>

    <!-- PIN Auth Modal Dialog -->
    <div id="auth-modal" class="modal-overlay">
        <div class="modal-content" style="max-width: 360px;">
            <div class="modal-header">
                <h2>🔒 存取密碼驗證 (PIN Required)</h2>
            </div>
            <div class="form-group" style="margin-top: 10px;">
                <label class="form-label">請輸入 4-6 位數存取 PIN 碼</label>
                <input type="password" id="auth-pin-input" class="form-control" placeholder="請輸入 PIN 碼" maxlength="12" onkeydown="if(event.key==='Enter') submitAuthPin()">
            </div>
            <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px;">
                <button class="btn btn-primary" onclick="submitAuthPin()">驗證解鎖</button>
            </div>
        </div>
    </div>

    <!-- Canvas Native Chart Section -->
    <details style="width: 100%; max-width: 900px; margin-top: 20px;" class="panel-card">
        <summary class="panel-title" style="cursor: pointer; outline: none; list-style: none;">
            <div style="display: inline-flex; align-items: center; gap: 8px;">
                <span>$sPerfTitle</span>
                <span style="font-size: 0.8rem; color: var(--accent-green);">$sPerfActive</span>
            </div>
            <span style="float: right; font-size: 0.8rem; color: var(--subtext);">$sPerfToggle</span>
        </summary>
        
        <div style="padding-top: 16px;">
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 8px; margin-bottom: 10px;">
                <div class="stat-box"><div class="stat-label">$sPerfCpu</div><div class="stat-val" id="cpu-stat-val">--%</div></div>
                <div class="stat-box"><div class="stat-label">$sPerfMem</div><div class="stat-val" id="mem-stat-val">--%</div></div>
                <div class="stat-box"><div class="stat-label">$sPerfPing</div><div class="stat-val" id="ping-stat-val">-- ms</div></div>
            </div>
            <canvas id="perf-canvas" width="800" height="160"></canvas>
        </div>
    </details>

    <details style="width: 100%; max-width: 900px; margin-top: 20px;" class="panel-card">
        <summary class="panel-title" style="cursor: pointer; outline: none; list-style: none; display: flex; justify-content: space-between; align-items: center;">
            <span>$sEventsTitle</span>
            <div style="display: flex; gap: 6px;">
                <button class="btn" style="padding: 4px 8px; font-size: 0.75rem;" onclick="event.stopPropagation(); fetchEvents()">$sEventsRefresh</button>
                <button class="btn btn-danger" style="padding: 4px 8px; font-size: 0.75rem;" onclick="event.stopPropagation(); clearAllEvents()">$sEventsClearAll</button>
            </div>
        </summary>
        
        <div style="padding-top: 16px;">
            <div id="events-container" style="display: grid; grid-template-columns: 1fr; gap: 10px; margin-top: 8px;">
                <div style="color: var(--subtext); text-align: center; padding: 16px;">$sEventsLoading</div>
            </div>
        </div>
    </details>

    <script>
        let isTorchOn = false;
        let currentAutoStart = true;
        let currentPowerCut = true;
        let currentRotation = 0;
        let currentZoom = 1.0;
        let panX = 0;
        let panY = 0;
        let perfHistory = [];

        const catNames = ['HUMAN_AND_ACTIVITY', 'PET_AND_ANIMAL', 'VEHICLE_AND_TRANSPORT', 'HOUSEHOLD_ITEM', 'ENVIRONMENT_AND_NATURE', 'OTHER'];

        function escapeHtml(str) {
            if (!str) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        function getAuthHeaders(customHeaders = {}) {
            const token = localStorage.getItem('ocular_session_token');
            const headers = { ...customHeaders };
            if (token) {
                headers['Authorization'] = 'Bearer ' + token;
                headers['X-Auth-Token'] = token;
            }
            return headers;
        }

        async function authFetch(url, options = {}) {
            options.headers = getAuthHeaders(options.headers || {});
            const res = await fetch(url, options);
            if (res.status === 401) {
                promptForPin();
            }
            return res;
        }

        function promptForPin() {
            const modal = document.getElementById('auth-modal');
            if (modal) {
                modal.style.display = 'flex';
                setTimeout(() => {
                    const input = document.getElementById('auth-pin-input');
                    if (input) input.focus();
                }, 100);
            }
        }

        async function submitAuthPin() {
            const input = document.getElementById('auth-pin-input');
            const pin = input ? input.value.trim() : '';
            if (!pin) return;
            try {
                const res = await fetch('/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ pin: pin })
                });
                const data = await res.json();
                if (data.status === 'ok' && data.token) {
                    localStorage.setItem('ocular_session_token', data.token);
                    document.cookie = 'session_token=' + encodeURIComponent(data.token) + '; path=/; max-age=86400';
                    document.getElementById('auth-modal').style.display = 'none';
                    if (input) input.value = '';
                    reloadStream();
                    fetchStatus();
                    fetchEvents();
                } else {
                    alert('PIN 碼錯誤，請重新輸入');
                    if (input) {
                        input.value = '';
                        input.focus();
                    }
                }
            } catch (e) {
                alert('驗證連線失敗: ' + e);
            }
        }

        async function openConfigModal() {
            try {
                const res = await authFetch('/config');
                const data = await res.json();
                
                if (data.device && data.device.deviceName) {
                    document.getElementById('cfg-dev-name').value = data.device.deviceName;
                }
                if (data.camera) {
                    if (data.camera.resolution) document.getElementById('cfg-resolution').value = data.camera.resolution;
                    if (data.camera.quality) document.getElementById('cfg-quality').value = String(data.camera.quality);
                }
                if (data.motionDetection) {
                    if (data.motionDetection.sensitivity) document.getElementById('cfg-motion-sens').value = data.motionDetection.sensitivity;
                    if (data.motionDetection.cooldownSeconds) document.getElementById('cfg-motion-cooldown').value = data.motionDetection.cooldownSeconds;
                    const mSched = document.getElementById('cfg-motion-sched-enable');
                    if (mSched) mSched.checked = data.motionDetection.scheduleEnabled === true;
                    if (data.motionDetection.scheduleStart) document.getElementById('cfg-motion-sched-start').value = data.motionDetection.scheduleStart;
                    if (data.motionDetection.scheduleEnd) document.getElementById('cfg-motion-sched-end').value = data.motionDetection.scheduleEnd;
                    if (data.motionDetection.categories) {
                        catNames.forEach(cat => {
                            const el = document.getElementById('cfg-cat-' + cat);
                            if (el) el.checked = data.motionDetection.categories[cat] !== false;
                        });
                    }
                }
                if (data.recording) {
                    document.getElementById('cfg-event-recording').checked = data.recording.eventRecordingEnabled !== false;
                    if (data.recording.maxStorageGb) document.getElementById('cfg-max-storage').value = data.recording.maxStorageGb;
                    if (data.recording.categoryRecording) {
                        catNames.forEach(cat => {
                            const el = document.getElementById('cfg-rec-cat-' + cat);
                            if (el) el.checked = data.recording.categoryRecording[cat] !== false;
                        });
                    }
                }
                if (data.security) {
                    const authCb = document.getElementById('cfg-auth-enable');
                    if (authCb) authCb.checked = data.security.httpAuthEnabled === true;
                    const pinInput = document.getElementById('cfg-auth-pin');
                    if (pinInput) pinInput.value = '';
                }
                if (data.notifications) {
                    document.getElementById('cfg-power-cut').checked = data.notifications.powerCutAlertEnabled !== false;
                    document.getElementById('cfg-sys-log').checked = data.notifications.systemLogEnabled !== false;
                    const nSched = document.getElementById('cfg-notif-sched-enable');
                    if (nSched) nSched.checked = data.notifications.scheduleEnabled === true;
                    if (data.notifications.scheduleStart) document.getElementById('cfg-notif-sched-start').value = data.notifications.scheduleStart;
                    if (data.notifications.scheduleEnd) document.getElementById('cfg-notif-sched-end').value = data.notifications.scheduleEnd;
                    if (data.notifications.telegram) {
                        document.getElementById('cfg-tg-token').value = data.notifications.telegram.botToken || '';
                        document.getElementById('cfg-tg-chat').value = data.notifications.telegram.chatId || '';
                    }
                }

                document.getElementById('config-modal').style.display = 'flex';
            } catch (e) {
                alert('$sJsConfigLoadFail' + e);
            }
        }

        function closeConfigModal() {
            document.getElementById('config-modal').style.display = 'none';
        }

        async function saveConfigBatch() {
            const catObj = {};
            const recCatObj = {};
            catNames.forEach(cat => {
                const el = document.getElementById('cfg-cat-' + cat);
                if (el) catObj[cat] = el.checked;
                const recEl = document.getElementById('cfg-rec-cat-' + cat);
                if (recEl) recCatObj[cat] = recEl.checked;
            });

            const configPayload = {
                device: {
                    deviceName: document.getElementById('cfg-dev-name').value
                },
                camera: {
                    resolution: document.getElementById('cfg-resolution').value,
                    quality: parseInt(document.getElementById('cfg-quality').value) || 75
                },
                motionDetection: {
                    enabled: true,
                    sensitivity: parseFloat(document.getElementById('cfg-motion-sens').value) || 5.0,
                    cooldownSeconds: parseInt(document.getElementById('cfg-motion-cooldown').value) || 10,
                    scheduleEnabled: document.getElementById('cfg-motion-sched-enable')?.checked || false,
                    scheduleStart: document.getElementById('cfg-motion-sched-start')?.value || '22:00',
                    scheduleEnd: document.getElementById('cfg-motion-sched-end')?.value || '06:00',
                    categories: catObj
                },
                recording: {
                    eventRecordingEnabled: document.getElementById('cfg-event-recording').checked,
                    maxStorageGb: parseFloat(document.getElementById('cfg-max-storage').value) || 10.0,
                    categoryRecording: recCatObj
                },
                security: {
                    httpAuthEnabled: document.getElementById('cfg-auth-enable')?.checked || false,
                    httpPinCode: document.getElementById('cfg-auth-pin')?.value || ''
                },
                notifications: {
                    powerCutAlertEnabled: document.getElementById('cfg-power-cut').checked,
                    systemLogEnabled: document.getElementById('cfg-sys-log').checked,
                    scheduleEnabled: document.getElementById('cfg-notif-sched-enable')?.checked || false,
                    scheduleStart: document.getElementById('cfg-notif-sched-start')?.value || '22:00',
                    scheduleEnd: document.getElementById('cfg-notif-sched-end')?.value || '06:00',
                    telegram: {
                        botToken: document.getElementById('cfg-tg-token').value,
                        chatId: document.getElementById('cfg-tg-chat').value
                    }
                }
            };

            try {
                const res = await authFetch('/config', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(configPayload)
                });
                const result = await res.json();
                if (result.status === 'ok') {
                    alert('$sJsConfigSaved');
                    closeConfigModal();
                    fetchStatus();
                } else {
                    alert('$sJsConfigSaveFail' + (result.message || 'Unknown error'));
                }
            } catch (e) {
                alert('$sJsConfigSendFail' + e);
            }
        }

        function reloadStream() {
            const img = document.getElementById('stream');
            if (img) {
                const token = localStorage.getItem('ocular_session_token');
                const tokenQuery = token ? '&token=' + encodeURIComponent(token) : '';
                img.src = '/mjpeg?t=' + Date.now() + tokenQuery;
                document.getElementById('stream-status-tag').innerText = '$sConnected';
                document.getElementById('stream-status-tag').style.background = 'var(--accent-green)';
            }
        }

        function onStreamError() {
            console.warn('MJPEG stream connection error, trying snapshot fallback...');
            const tag = document.getElementById('stream-status-tag');
            if (tag) {
                tag.innerText = '$sJsSnapshotMode';
                tag.style.background = '#F59E0B';
            }
            const img = document.getElementById('stream');
            if (img) {
                const token = localStorage.getItem('ocular_session_token');
                const tokenQuery = token ? '&token=' + encodeURIComponent(token) : '';
                img.src = '/snapshot?t=' + Date.now() + tokenQuery;
            }
        }

        function rotateLocal(deltaDeg) {
            currentRotation = (currentRotation + deltaDeg + 360) % 360;
            applyTransform();
        }

        async function rotateStreamServer() {
            await sendCommand('rotation', '+1');
            setTimeout(fetchStatus, 300);
        }

        function zoom(deltaScale) {
            currentZoom = Math.min(Math.max(currentZoom + deltaScale, 1.0), 5.0);
            if (currentZoom === 1.0) { panX = 0; panY = 0; }
            const videoCard = document.querySelector('.video-card');
            if (videoCard) {
                videoCard.style.cursor = currentZoom > 1.0 ? 'grab' : 'default';
            }
            applyTransform();
        }

        function resetZoom() {
            currentZoom = 1.0;
            panX = 0;
            panY = 0;
            const videoCard = document.querySelector('.video-card');
            if (videoCard) {
                videoCard.style.cursor = 'default';
            }
            applyTransform();
        }

        function applyTransform() {
            const img = document.getElementById('stream');
            if (!img) return;
            const badge = document.getElementById('zoom-badge');
            if (badge) badge.innerText = currentZoom.toFixed(1) + 'x';
            img.style.transform = 'translate(' + panX + 'px, ' + panY + 'px) rotate(' + currentRotation + 'deg) scale(' + currentZoom + ')';
        }

        function initVideoControls() {
            const videoCard = document.querySelector('.video-card');
            if (!videoCard) return;

            // Mouse wheel zoom
            videoCard.addEventListener('wheel', function(e) {
                e.preventDefault();
                const delta = e.deltaY < 0 ? 0.25 : -0.25;
                zoom(delta);
            }, { passive: false });

            // Drag / Pan variables
            let isDragging = false;
            let startX = 0, startY = 0;

            videoCard.addEventListener('mousedown', function(e) {
                if (currentZoom > 1.0) {
                    isDragging = true;
                    startX = e.clientX - panX;
                    startY = e.clientY - panY;
                    videoCard.style.cursor = 'grabbing';
                    e.preventDefault();
                }
            });

            window.addEventListener('mousemove', function(e) {
                if (isDragging) {
                    panX = e.clientX - startX;
                    panY = e.clientY - startY;
                    applyTransform();
                }
            });

            window.addEventListener('mouseup', function() {
                if (isDragging) {
                    isDragging = false;
                    videoCard.style.cursor = currentZoom > 1.0 ? 'grab' : 'default';
                }
            });

            // Touch support for mobile browsers
            videoCard.addEventListener('touchstart', function(e) {
                if (currentZoom > 1.0 && e.touches.length === 1) {
                    isDragging = true;
                    startX = e.touches[0].clientX - panX;
                    startY = e.touches[0].clientY - panY;
                }
            }, { passive: true });

            window.addEventListener('touchmove', function(e) {
                if (isDragging && e.touches.length === 1) {
                    panX = e.touches[0].clientX - startX;
                    panY = e.touches[0].clientY - startY;
                    applyTransform();
                }
            }, { passive: true });

            window.addEventListener('touchend', function() {
                isDragging = false;
            });
        }

        function drawPerfCanvas() {
            const canvas = document.getElementById('perf-canvas');
            if (!canvas) return;
            const ctx = canvas.getContext('2d');
            const w = canvas.width;
            const h = canvas.height;

            ctx.clearRect(0, 0, w, h);
            ctx.fillStyle = '#0F172A';
            ctx.fillRect(0, 0, w, h);

            // Grid lines
            ctx.strokeStyle = '#334155';
            ctx.lineWidth = 1;
            for (let y = 20; y < h; y += 35) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(w, y);
                ctx.stroke();
            }

            if (perfHistory.length < 2) return;

            const maxPoints = 20;
            const stepX = w / (maxPoints - 1);

            // Draw CPU line
            ctx.strokeStyle = '#6750A4';
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            for (let i = 0; i < perfHistory.length; i++) {
                const x = i * stepX;
                const val = perfHistory[i].cpu || 0;
                const y = h - (val / 100 * (h - 20)) - 10;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            }
            ctx.stroke();

            // Draw Memory line
            ctx.strokeStyle = '#22C55E';
            ctx.lineWidth = 2.5;
            ctx.beginPath();
            for (let i = 0; i < perfHistory.length; i++) {
                const x = i * stepX;
                const val = perfHistory[i].mem || 0;
                const y = h - (val / 100 * (h - 20)) - 10;
                if (i === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
            }
            ctx.stroke();

            // Legend
            ctx.fillStyle = '#6750A4';
            ctx.fillRect(10, 10, 12, 12);
            ctx.fillStyle = '#F8FAFC';
            ctx.font = '11px sans-serif';
            ctx.fillText('CPU %', 28, 20);

            ctx.fillStyle = '#22C55E';
            ctx.fillRect(80, 10, 12, 12);
            ctx.fillStyle = '#F8FAFC';
            ctx.fillText('$sPerfRamLegend', 98, 20);
        }

        async function fetchStatus() {
            try {
                const startTime = Date.now();
                const res = await authFetch('/status');
                if (res.status === 401) return;
                const ping = Date.now() - startTime;
                const data = await res.json();

                const devName = document.getElementById('dev-name');
                if (devName) devName.innerText = data.deviceName || 'OcularNode Camera';

                let battTxt = (data.batteryLevel >= 0 ? data.batteryLevel + '%' : '--');
                if (data.batteryTemp && data.batteryTemp > 0) battTxt += ' (' + data.batteryTemp.toFixed(1) + '°C)';
                if (data.isThermalThrottled) battTxt += ' $sJsHotBattery';
                const battEl = document.getElementById('battery-val');
                if (battEl) battEl.innerText = battTxt;

                const rotVal = data.streamRotation !== undefined ? data.streamRotation : 0;
                const rotEl = document.getElementById('rotation-val');
                if (rotEl) rotEl.innerText = rotVal + '°';

                const clientsEl = document.getElementById('clients-val');
                if (clientsEl) clientsEl.innerText = (data.connectedClients || 0) + ' $sStatViewersSuffix';

                const fpsEl = document.getElementById('fps-val');
                if (fpsEl) fpsEl.innerText = (data.fps || 0) + ' FPS';
                const statFpsEl = document.getElementById('stat-fps-val');
                if (statFpsEl) statFpsEl.innerText = (data.fps || 0) + ' FPS';

                const resBadge = document.getElementById('res-badge');
                if (resBadge) {
                    const resStr = data.resolution || '720p';
                    const qualStr = data.quality !== undefined ? ' (Quality ' + data.quality + '%)' : '';
                    resBadge.innerText = resStr + qualStr;
                }

                const cpu = data.cpuUsage || 30;
                const mem = data.memoryUsage || 45;
                const cpuEl = document.getElementById('cpu-stat-val');
                if (cpuEl) cpuEl.innerText = cpu + '%';

                const memEl = document.getElementById('mem-stat-val');
                if (memEl) memEl.innerText = mem + '%';

                const pingEl = document.getElementById('ping-stat-val');
                if (pingEl) pingEl.innerText = ping + ' ms';

                perfHistory.push({ cpu: cpu, mem: mem });
                if (perfHistory.length > 20) perfHistory.shift();
                drawPerfCanvas();

                const opMode = data.operatingMode || 'monitor';
                const modeEl = document.getElementById('mode-val');
                if (modeEl) modeEl.innerText = (opMode === 'monitor' ? '$sModeMonitor' : '$sModeDetection');

                const btnMon = document.getElementById('btn-mode-monitor');
                if (btnMon) btnMon.style.background = (opMode === 'monitor' ? 'var(--primary)' : '#334155');
                const btnDet = document.getElementById('btn-mode-detection');
                if (btnDet) btnDet.style.background = (opMode === 'detection' ? 'var(--primary)' : '#334155');

                const nMode = data.nightVisionMode || 'auto';
                const nightEl = document.getElementById('night-val');
                if (nightEl) nightEl.innerText = (data.isNightVisionActive ? '$sJsNightActive (' + nMode + ')' : '$sJsNightNormal (' + nMode + ')');

                const btnNOff = document.getElementById('btn-night-off');
                if (btnNOff) btnNOff.style.background = (nMode === 'off' ? 'var(--primary)' : '#334155');
                const btnNOn = document.getElementById('btn-night-on');
                if (btnNOn) btnNOn.style.background = (nMode === 'on' ? 'var(--primary)' : '#334155');
                const btnNAuto = document.getElementById('btn-night-auto');
                if (btnNAuto) btnNAuto.style.background = (nMode === 'auto' ? 'var(--primary)' : '#334155');

                if (data.storageFree && data.storageTotal) {
                    const storEl = document.getElementById('storage-val');
                    if (storEl) storEl.innerText = data.storageFree + ' / ' + data.storageTotal;
                }
            } catch (e) {
                console.error('fetchStatus error:', e);
            }
        }

        async function fetchEvents() {
            try {
                const res = await authFetch('/events');
                if (res.status === 401) return;
                const events = await res.json();
                const container = document.getElementById('events-container');
                if (!container) return;

                if (!Array.isArray(events) || events.length === 0) {
                    container.innerHTML = '<div style="color: var(--subtext); text-align: center; padding: 16px;">$sEventsEmpty</div>';
                    return;
                }

                const token = localStorage.getItem('ocular_session_token');
                const tokenSuffix = token ? '&token=' + encodeURIComponent(token) : '';

                var htmlStr = '';
                for (var i = 0; i < events.length; i++) {
                    var ev = events[i];
                    var imgTag = ev.thumbnailBase64 ? '<img src="data:image/jpeg;base64,' + ev.thumbnailBase64 + '" style="width:84px; height:64px; object-fit:cover; border-radius:8px; border:1px solid #334155;">' : '<div style="width:84px; height:64px; background:#1E293B; border-radius:8px; display:flex; align-items:center; justify-content:center; color:#94A3B8;">📷</div>';
                    
                    var videoUrl = (ev.videoUrl || '') + tokenSuffix;
                    var downloadUrl = (ev.downloadUrl || '') + tokenSuffix;
                    var videoBtn = ev.hasVideo ? '<a href="' + escapeHtml(videoUrl) + '" target="_blank" class="btn" style="background:#2563EB; font-size:0.8rem; padding:4px 8px;">$sEventsVideo</a>' : '';

                    var aiTag = ev.aiSummary ? '<div style="font-size:0.75rem; color:#A78BFA; margin-top:2px;">' + escapeHtml(ev.aiSummary) + '</div>' : '';

                    htmlStr += '<div style="background:#0F172A; border:1px solid #334155; border-radius:12px; padding:10px; display:flex; align-items:center; gap:12px; flex-wrap:wrap;">' +
                        imgTag +
                        '<div style="flex:1; min-width:160px;">' +
                        '<div style="font-weight:bold; font-size:0.95rem; color:#F1F5F9;">$sEventsMotionPrefix (' + escapeHtml(ev.motionPercentage) + '%)</div>' +
                        '<div style="font-size:0.8rem; color:var(--subtext); margin-top:2px;">📅 ' + escapeHtml(ev.formattedTime) + '</div>' +
                        aiTag +
                        '</div>' +
                        '<div style="display:flex; gap:6px; align-items:center;">' +
                        '<a href="' + escapeHtml(downloadUrl) + '" download class="btn btn-primary" style="font-size:0.8rem; padding:4px 8px;">$sEventsPhoto</a>' +
                        videoBtn +
                        '<button onclick="deleteEvent(' + ev.id + ')" class="btn btn-danger" style="padding:4px 8px;">🗑️</button>' +
                        '</div>' +
                        '</div>';
                }
                container.innerHTML = htmlStr;
            } catch (e) {
                console.error('fetchEvents error:', e);
            }
        }

        async function deleteEvent(id) {
            if (confirm('$sJsDelConfirm')) {
                try {
                    await authFetch('/events/delete?id=' + id);
                    fetchEvents();
                } catch (e) {
                    alert('$sJsDelFail' + e);
                }
            }
        }

        async function clearAllEvents() {
            if (confirm('$sJsClearConfirm')) {
                try {
                    await authFetch('/events/clear');
                    fetchEvents();
                } catch (e) {
                    alert('$sJsClearFail' + e);
                }
            }
        }

        async function sendCommand(cmd, val) {
            if (cmd === 'torch' && val === 'toggle') {
                val = isTorchOn ? 'off' : 'on';
            }
            try {
                await authFetch('/control', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ command: cmd, value: val })
                });
                setTimeout(fetchStatus, 300);
            } catch (e) {
                alert('$sJsCmdFail' + e);
            }
        }

        function takeSnapshot() {
            const img = document.getElementById('stream');
            if (!img) return;
            const w = img.naturalWidth || 1280;
            const h = img.naturalHeight || 720;
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            if (currentRotation === 90 || currentRotation === 270) {
                canvas.width = h; canvas.height = w;
            } else {
                canvas.width = w; canvas.height = h;
            }
            ctx.translate(canvas.width / 2, canvas.height / 2);
            ctx.rotate((currentRotation * Math.PI) / 180);
            ctx.drawImage(img, -w / 2, -h / 2);
            const a = document.createElement('a');
            a.href = canvas.toDataURL('image/jpeg');
            a.download = 'OcularNode_snapshot_' + Date.now() + '.jpg';
            a.click();
        }

        let audioCtx = null;
        let audioReader = null;
        let isAudioListening = false;
        let nextPlayTime = 0;

        async function toggleAudioListen() {
            const btn = document.getElementById('btn-audio-listen');
            if (isAudioListening) {
                stopAudioListen();
                return;
            }
            try {
                audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                if (audioCtx.state === 'suspended') {
                    await audioCtx.resume();
                }
                nextPlayTime = audioCtx.currentTime;

                const token = localStorage.getItem('ocular_session_token');
                const tokenParam = token ? '?token=' + encodeURIComponent(token) : '';
                const response = await authFetch('/audio' + tokenParam);
                if (response.status === 401) {
                    stopAudioListen();
                    return;
                }
                if (!response.body) {
                    alert('$sJsAudioUnsupported');
                    return;
                }
                audioReader = response.body.getReader();
                isAudioListening = true;
                if (btn) {
                    btn.innerText = '$sAudioMute';
                    btn.style.background = '#EF4444';
                }
                readAudioStream();
            } catch (e) {
                alert('$sJsAudioFail' + e);
                stopAudioListen();
            }
        }

        function stopAudioListen() {
            isAudioListening = false;
            if (audioReader) {
                try { audioReader.cancel(); } catch(_){}
                audioReader = null;
            }
            if (audioCtx) {
                try { audioCtx.close(); } catch(_){}
                audioCtx = null;
            }
            const btn = document.getElementById('btn-audio-listen');
            if (btn) {
                btn.innerText = '$sAudioListen';
                btn.style.background = '#334155';
            }
        }

        async function readAudioStream() {
            let leftover = new Uint8Array(0);
            while (isAudioListening && audioReader) {
                try {
                    const { done, value } = await audioReader.read();
                    if (done) break;
                    if (!value || value.length === 0) continue;

                    let totalLen = leftover.length + value.length;
                    let combined = new Uint8Array(totalLen);
                    combined.set(leftover, 0);
                    combined.set(value, leftover.length);

                    let samplesCount = Math.floor(totalLen / 2);
                    if (samplesCount === 0) {
                        leftover = combined;
                        continue;
                    }

                    let usedBytes = samplesCount * 2;
                    leftover = combined.slice(usedBytes);

                    let dataView = new DataView(combined.buffer, combined.byteOffset, usedBytes);
                    let float32Array = new Float32Array(samplesCount);
                    for (let i = 0; i < samplesCount; i++) {
                        let int16 = dataView.getInt16(i * 2, true);
                        float32Array[i] = int16 / 32768.0;
                    }

                    if (audioCtx) {
                        let audioBuffer = audioCtx.createBuffer(1, samplesCount, 16000);
                        audioBuffer.getChannelData(0).set(float32Array);

                        let source = audioCtx.createBufferSource();
                        source.buffer = audioBuffer;
                        source.connect(audioCtx.destination);

                        let currentTime = audioCtx.currentTime;
                        if (nextPlayTime < currentTime) {
                            nextPlayTime = currentTime + 0.05;
                        }
                        source.start(nextPlayTime);
                        nextPlayTime += audioBuffer.duration;
                    }
                } catch (e) {
                    console.error('Audio stream read error:', e);
                    break;
                }
            }
            stopAudioListen();
        }

        fetchStatus();
        fetchEvents();
        setInterval(fetchStatus, 2000);
        setInterval(fetchEvents, 10000);
        applyTransform();
        initVideoControls();
    </script>
</body>
</html>""".trimIndent()
    }
}
