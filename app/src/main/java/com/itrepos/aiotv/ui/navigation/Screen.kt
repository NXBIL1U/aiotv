package com.itrepos.aiotv.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Search : Screen("search")
    object Live : Screen("live")
    object Watchlist : Screen("watchlist")
    object Addons : Screen("addons")
    object Settings : Screen("settings")
    object Player : Screen("player/{url}/{title}/{progressId}") {
        fun createRoute(url: String, title: String, progressId: String = url) =
            "player/${enc(url)}/${enc(title)}/${enc(progressId)}"
        private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    }
    object Detail : Screen("detail/{type}/{id}") {
        fun createRoute(type: String, id: String) = "detail/$type/$id"
    }
}
