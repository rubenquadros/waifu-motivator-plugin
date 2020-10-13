package zd.zero.waifu.motivator.plugin.assets

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import zd.zero.waifu.motivator.plugin.tools.toOptional
import java.util.*
import kotlin.random.Random

abstract class RemoteAssetDefinitionService<T : AssetDefinition, U : Asset>(
    private val remoteAssetManager: RemoteAssetManager<T, U>
) {
    private val random = Random(System.currentTimeMillis())

    fun getAssetByGroupId(
        groupId: UUID,
        category: WaifuAssetCategory
    ): Optional<U> =
            remoteAssetManager.supplyLocalAssetDefinitions()
                .find {
                    it.groupId == groupId
                }.toOptional()
                .map {
                    resolveAsset(category, it)
                }.orElseGet {
                    remoteAssetManager.supplyRemoteAssetDefinitions()
                        .find { it.groupId == groupId }
                        .toOptional()
                        .map { remoteAssetManager.resolveAsset(it) }
                        .orElseGet {
                            getRandomAssetByCategory(category)
                        }
                }

    fun getRandomAssetByCategory(
        waifuAssetCategory: WaifuAssetCategory
    ): Optional<U> =
        pickRandomAsset(remoteAssetManager.supplyLocalAssetDefinitions(), waifuAssetCategory)
            .map {
                resolveAsset(waifuAssetCategory, it)
            }.orElseGet {
                fetchRemoteAsset(waifuAssetCategory)
            }

    private fun resolveAsset(
        waifuAssetCategory: WaifuAssetCategory,
        it: T
    ): Optional<U> {
        downloadNewAsset(waifuAssetCategory)
        return remoteAssetManager.resolveAsset(it)
    }

    @VisibleForTesting
    protected open fun downloadNewAsset(waifuAssetCategory: WaifuAssetCategory) {
        ApplicationManager.getApplication().executeOnPooledThread { fetchRemoteAsset(waifuAssetCategory) }
    }

    private fun fetchRemoteAsset(
        waifuAssetCategory: WaifuAssetCategory
    ): Optional<U> =
        pickRandomAsset(remoteAssetManager.supplyRemoteAssetDefinitions(), waifuAssetCategory)
            .flatMap { remoteAssetManager.resolveAsset(it) }

    private fun pickRandomAsset(
        assetDefinitions: Collection<T>,
        waifuAssetCategory: WaifuAssetCategory
    ): Optional<T> =
        assetDefinitions
            .filter { it.categories.contains(waifuAssetCategory) }
            .toOptional()
            .filter { it.isNotEmpty() }
            .map { it.random(random) }
}
