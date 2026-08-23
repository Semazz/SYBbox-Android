package com.sybbox.data.repository

import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.db.ProfileDao
import com.sybbox.data.db.entity.ProfileEntity
import com.sybbox.domain.model.BypassPreset
import com.sybbox.domain.model.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore,
) {
    fun getAllProfiles(): Flow<List<ServerProfile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getProfilesBySubscription(subId: Long): Flow<List<ServerProfile>> {
        return profileDao.getProfilesBySubscription(subId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getEnabledProfiles(): Flow<List<ServerProfile>> {
        return profileDao.getEnabledProfiles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getProfileById(id: Long): ServerProfile? {
        return profileDao.getProfileById(id)?.toDomain()
    }

    suspend fun insertProfile(profile: ServerProfile): Long {
        return profileDao.insertProfile(profile.toEntity())
    }

    suspend fun insertProfiles(profiles: List<ServerProfile>): List<Long> {
        return profileDao.insertProfiles(profiles.map { it.toEntity() })
    }

    suspend fun updateProfile(profile: ServerProfile) {
        profileDao.updateProfile(profile.toEntity())
    }

    suspend fun deleteProfile(profile: ServerProfile) {
        profileDao.deleteProfile(profile.toEntity())
    }

    suspend fun deleteProfileById(id: Long) {
        profileDao.deleteProfileById(id)
    }

    suspend fun deleteProfilesBySubscription(subId: Long) {
        profileDao.deleteProfilesBySubscription(subId)
    }

    suspend fun mergeSubscriptionProfiles(subscriptionId: Long, incoming: List<ServerProfile>): List<Long> {
        val existing = profileDao.getProfilesBySubscriptionOnce(subscriptionId)
        val byKey = existing.associateBy { mergeKey(it) }

        val keptIds = mutableSetOf<Long>()
        val resultIds = ArrayList<Long>(incoming.size)
        val toInsert = ArrayList<ServerProfile>()
        val toUpdate = ArrayList<ProfileEntity>()

        for (profile in incoming) {
            val entity = profile.copy(subscriptionId = subscriptionId).toEntity()
            val match = byKey[mergeKey(entity)]
            if (match != null && keptIds.add(match.id)) {

                toUpdate += entity.copy(id = match.id, createdAt = match.createdAt, lastLatency = match.lastLatency)
                resultIds += match.id
            } else {
                toInsert += profile
            }
        }
        val insertedIds = if (toInsert.isEmpty()) emptyList() else insertProfiles(toInsert)
        toUpdate.forEach { profileDao.updateProfile(it) }
        resultIds.addAll(insertedIds)

        val keepAll = keptIds + insertedIds.toSet()
        existing.filter { it.id !in keepAll }.forEach { profileDao.deleteProfileById(it.id) }
        return resultIds
    }

    private fun mergeKey(entity: ProfileEntity) = "${entity.protocol}|${entity.address}|${entity.port}"

    suspend fun updateLatency(id: Long, latency: Int) {
        profileDao.updateLatency(id, latency)
    }

    suspend fun setLastProfile(id: Long) {
        settingsDataStore.setLastProfileId(id)
    }

    suspend fun getLastProfileId(): Flow<Long> = settingsDataStore.lastProfileId
}

private fun ProfileEntity.toDomain() = ServerProfile(
    id = id, name = name, address = address, port = port,
    protocol = com.sybbox.domain.model.ProtocolType.valueOf(protocol),
    uuid = uuid, alterId = alterId, flow = flow,
    security = com.sybbox.domain.model.SecurityType.valueOf(security),
    encryption = encryption,
    transport = com.sybbox.domain.model.TransportType.valueOf(transport),
    subscriptionId = subscriptionId, serverName = serverName,
    fingerprint = fingerprint, allowInsecure = allowInsecure,
    alpn = alpn.split(",").filter { it.isNotBlank() },
    realityPublicKey = realityPublicKey, realityShortId = realityShortId,
    realityFingerprint = realityFingerprint, wsPath = wsPath, wsHost = wsHost,
    maxEarlyData = maxEarlyData, h2Host = h2Host, h2Path = h2Path,
    grpcServiceName = grpcServiceName, multiplexEnabled = multiplexEnabled,
    multiplexProtocol = multiplexProtocol, multiplexMaxStreams = multiplexMaxStreams,
    multiplexPadding = multiplexPadding, hy2Password = hy2Password,
    hy2UpMbps = hy2UpMbps, hy2DownMbps = hy2DownMbps,
    hy2ObfsType = hy2ObfsType, hy2ObfsPassword = hy2ObfsPassword,
    tuicPassword = tuicPassword, tuicCongestionControl = tuicCongestionControl,
    wgPrivateKey = wgPrivateKey, wgPeerPublicKey = wgPeerPublicKey,
    wgPresharedKey = wgPresharedKey,
    wgReserved = wgReserved.split(",").mapNotNull { it.toIntOrNull() },
    wgLocalAddress = wgLocalAddress, wgMTU = wgMTU,
    shadowTlsPassword = shadowTlsPassword, shadowTlsVersion = shadowTlsVersion,
    anytlsPassword = anytlsPassword, anytlsMinIdleSession = anytlsMinIdleSession,
    ssPassword = ssPassword, ssMethod = ssMethod,
    bypassPreset = BypassPreset.valueOf(bypassPreset),
    recordFragment = recordFragment, echEnabled = echEnabled,
    tlsSpoof = tlsSpoof, tlsSpoofMethod = tlsSpoofMethod,
    disableSni = disableSni, lastLatency = lastLatency,
    enabled = enabled, createdAt = createdAt,
)

private fun ServerProfile.toEntity() = ProfileEntity(
    id = id, name = name, address = address, port = port,
    protocol = protocol.name, uuid = uuid, alterId = alterId,
    flow = flow, security = security.name, encryption = encryption,
    transport = transport.name, subscriptionId = subscriptionId,
    serverName = serverName, fingerprint = fingerprint,
    allowInsecure = allowInsecure, alpn = alpn.joinToString(","),
    realityPublicKey = realityPublicKey, realityShortId = realityShortId,
    realityFingerprint = realityFingerprint, wsPath = wsPath, wsHost = wsHost,
    maxEarlyData = maxEarlyData, h2Host = h2Host, h2Path = h2Path,
    grpcServiceName = grpcServiceName, multiplexEnabled = multiplexEnabled,
    multiplexProtocol = multiplexProtocol, multiplexMaxStreams = multiplexMaxStreams,
    multiplexPadding = multiplexPadding, hy2Password = hy2Password,
    hy2UpMbps = hy2UpMbps, hy2DownMbps = hy2DownMbps,
    hy2ObfsType = hy2ObfsType, hy2ObfsPassword = hy2ObfsPassword,
    tuicPassword = tuicPassword, tuicCongestionControl = tuicCongestionControl,
    wgPrivateKey = wgPrivateKey, wgPeerPublicKey = wgPeerPublicKey,
    wgPresharedKey = wgPresharedKey, wgReserved = wgReserved.joinToString(","),
    wgLocalAddress = wgLocalAddress, wgMTU = wgMTU,
    shadowTlsPassword = shadowTlsPassword, shadowTlsVersion = shadowTlsVersion,
    anytlsPassword = anytlsPassword, anytlsMinIdleSession = anytlsMinIdleSession,
    ssPassword = ssPassword, ssMethod = ssMethod,
    bypassPreset = bypassPreset.name, recordFragment = recordFragment,
    echEnabled = echEnabled, tlsSpoof = tlsSpoof,
    tlsSpoofMethod = tlsSpoofMethod, disableSni = disableSni,
    lastLatency = lastLatency, enabled = enabled, createdAt = createdAt,
)
