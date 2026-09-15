from pathlib import Path

p = Path('app/src/main/java/com/example/haritalar/ui/MainViewModel.kt')
s = p.read_text()

s = s.replace(
'import com.example.haritalar.data.repository.TrafficSignalRepository\n',
'import com.example.BuildConfig\nimport com.example.haritalar.data.repository.TrafficSignalRepository\nimport com.example.haritalar.data.network.LiveSharingClient\n'
)

s = s.replace(
'    private var trafficSignalJob: Job? = null\n',
'    private var trafficSignalJob: Job? = null\n    private var liveShareJob: Job? = null\n    private var liveShareSession: LiveSharingClient.Session? = null\n    private val liveSharingClient = LiveSharingClient(BuildConfig.LIVE_SHARE_BASE_URL)\n'
)

old = '''    fun toggleLiveSharing() {\n        _uiState.value = _uiState.value.copy(\n            isLiveSharingActive = false,\n            liveShareUrl = null,\n            statusMessage = "Canlı paylaşım şu anda devre dışı: doğrulanmış paylaşım sunucusu bağlı değil."\n        )\n    }\n'''
new = '''    fun toggleLiveSharing() {\n        if (liveShareSession != null) {\n            val session = liveShareSession\n            liveShareSession = null\n            liveShareJob?.cancel()\n            liveShareJob = null\n            _uiState.value = _uiState.value.copy(isLiveSharingActive = false, liveShareUrl = null)\n            viewModelScope.launch {\n                runCatching { liveSharingClient.revoke(session!!.id, session.token) }\n            }\n            return\n        }\n\n        val location = currentRoutingLocation() ?: run {\n            _uiState.value = _uiState.value.copy(statusMessage = "Canlı paylaşım için gerçek GPS konumu gerekli.")\n            return\n        }\n\n        liveShareJob?.cancel()\n        liveShareJob = viewModelScope.launch {\n            try {\n                val eta = _uiState.value.navigationProgress?.totalRemainingSeconds\n                val session = liveSharingClient.createSession(\n                    latitude = location.point.latitude,\n                    longitude = location.point.longitude,\n                    bearing = location.bearing,\n                    speedKmh = location.speedKmh,\n                    etaSeconds = eta\n                )\n                liveShareSession = session\n                _uiState.value = _uiState.value.copy(\n                    isLiveSharingActive = true,\n                    liveShareUrl = session.viewerUrl,\n                    statusMessage = "Canlı takip paylaşımı açıldı."\n                )\n\n                while (liveShareSession?.id == session.id) {\n                    val current = currentRoutingLocation() ?: break\n                    val currentEta = _uiState.value.navigationProgress?.totalRemainingSeconds\n                    runCatching {\n                        liveSharingClient.updateLocation(\n                            sessionId = session.id,\n                            token = session.token,\n                            latitude = current.point.latitude,\n                            longitude = current.point.longitude,\n                            bearing = current.bearing,\n                            speedKmh = current.speedKmh,\n                            etaSeconds = currentEta\n                        )\n                    }.onFailure {\n                        _uiState.value = _uiState.value.copy(statusMessage = "Canlı takip sunucusuna ulaşılamadı; paylaşım durduruldu.")\n                        liveShareSession = null\n                        _uiState.value = _uiState.value.copy(isLiveSharingActive = false, liveShareUrl = null)\n                        break\n                    }\n                    delay(15_000L)\n                }\n            } catch (e: CancellationException) {\n                throw e\n            } catch (e: Exception) {\n                liveShareSession = null\n                _uiState.value = _uiState.value.copy(\n                    isLiveSharingActive = false,\n                    liveShareUrl = null,\n                    statusMessage = "Canlı paylaşım başlatılamadı: ${e.message ?: "sunucu hatası"}"\n                )\n            }\n        }\n    }\n'''
if old not in s:
    raise SystemExit('toggleLiveSharing block not found')
s = s.replace(old, new)

s = s.replace(
'        routeCalculationJob?.cancel()\n        vehicleHeadingManager.stop()\n',
'        routeCalculationJob?.cancel()\n        liveShareJob?.cancel()\n        liveShareSession = null\n        vehicleHeadingManager.stop()\n'
)
p.write_text(s)
