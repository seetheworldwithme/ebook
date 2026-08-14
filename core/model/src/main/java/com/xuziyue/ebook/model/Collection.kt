package com.xuziyue.ebook.model

/**
 * 书架类型（LIB-05）。
 *
 * - [SYSTEM_FAVORITE]：系统内置「收藏」书架。固定 id（[SYSTEM_FAVORITE_ID]）、不可删、不可改名，
 *   迁移时自动插入（保证老用户升级后立即可用）。收藏一本书 = 加入该书架。
 * - [CUSTOM]：用户自建书架，可改名、可删（删除书架不删除书籍，仅清关系）。
 *
 * 「书架」「标签」「合集」语义合一（徐先生拍板）——一本书可属于多个书架，统一用 Collection 模型，零冗余。
 */
enum class CollectionKind { SYSTEM_FAVORITE, CUSTOM }

/**
 * 书架领域模型（LIB-05，对齐 design.md §6.4 的 Collection）。
 *
 * @property id 主键（UUID，应用层生成；系统书架固定 [SYSTEM_FAVORITE_ID]）。
 * @property name 展示名（系统书架恒为「收藏」/ "Favorites"，由 UI 资源显示）。
 * @property sortOrder 排序权重（越小越靠前；首版按创建序，拖拽重排留后）。
 * @property kind [CollectionKind]，决定是否可删/改名。
 * @property bookCount 书架内书籍数（展示派生字段，与 [LibraryItem.progression] 同理的展示模型）。
 */
data class Collection(
    val id: String,
    val name: String,
    val sortOrder: Long,
    val kind: CollectionKind,
    val bookCount: Int,
)

/** 系统书架「收藏」固定 id（迁移时插入；全应用唯一引用点）。 */
const val SYSTEM_FAVORITE_ID: String = "system-favorite"
