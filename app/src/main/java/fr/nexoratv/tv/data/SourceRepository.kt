package fr.nexoratv.tv.data

import fr.nexoratv.tv.core.model.PlaylistSource
import fr.nexoratv.tv.core.model.SourceKind
import fr.nexoratv.tv.core.model.XtreamOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRepository @Inject constructor(
    db: NexoraDatabase,
) {
    private val dao = db.sources()

    val sources: Flow<List<PlaylistSource>> = dao.all().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<PlaylistSource> = dao.allOnce().map { it.toModel() }

    suspend fun save(source: PlaylistSource, position: Int = 0) =
        dao.upsert(source.toEntity(position))

    suspend fun delete(id: String) = dao.delete(id)

    private fun SourceEntity.toModel() = PlaylistSource(
        id = id,
        name = name,
        kind = SourceKind.valueOf(kind),
        m3uUrl = m3uUrl,
        epgUrl = epgUrl,
        host = host,
        username = username,
        password = password,
        xtreamOutput = XtreamOutput.valueOf(xtreamOutput),
        activationMac = activationMac,
        createdAt = createdAt,
    )

    private fun PlaylistSource.toEntity(position: Int) = SourceEntity(
        id = id,
        name = name,
        kind = kind.name,
        m3uUrl = m3uUrl,
        epgUrl = epgUrl,
        host = host,
        username = username,
        password = password,
        xtreamOutput = xtreamOutput.name,
        activationMac = activationMac,
        createdAt = createdAt,
        position = position,
    )
}
