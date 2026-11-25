package com.dios.cinema

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TMDB_KEY = "487ccc6c1277963bca2c28be4fab0792"
private val BG = Color(0xFF101310)
private val PANEL = Color(0xFF1B211C)
private val LIME = Color(0xFFC5F36A)
private val MUTED = Color(0xFF9AA596)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DiosCinemaApp { link ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
            }
        }
    }
}

data class Film(val id: String, val title: String, val overview: String, val poster: String, val year: String, val rating: String, val link: String = "")

private suspend fun json(url: String): JSONObject = withContext(Dispatchers.IO) {
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 12000
    c.readTimeout = 15000
    c.setRequestProperty("User-Agent", "DIOS-Cinema/1.1.0")
    try { JSONObject(c.inputStream.bufferedReader().use { it.readText() }) } finally { c.disconnect() }
}

@Composable
fun DiosCinemaApp(openLink: (String) -> Unit) {
    var tab by remember { mutableStateOf("Главная") }
    var query by remember { mutableStateOf("") }
    var films by remember { mutableStateOf(listOf<Film>()) }
    var archive by remember { mutableStateOf(listOf<Film>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Film?>(null) }
    var sort by remember { mutableStateOf(false) }
    var ratingFilter by remember { mutableStateOf(false) }
    val favorites = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()

    fun load(search: String = "") {
        scope.launch {
            loading = true; error = ""
            try {
                val endpoint = if (search.isBlank())
                    "https://api.themoviedb.org/3/trending/movie/week?api_key=" + TMDB_KEY + "&language=ru-RU"
                else "https://api.themoviedb.org/3/search/movie?api_key=" + TMDB_KEY + "&language=ru-RU&query=" + URLEncoder.encode(search, "UTF-8")
                val arr = json(endpoint).optJSONArray("results")
                val out = mutableListOf<Film>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val poster = o.optString("poster_path")
                    out.add(Film(o.optString("id"), o.optString("title", "Без названия"),
                        o.optString("overview", "Описание отсутствует"),
                        if (poster.isBlank() || poster == "null") "" else "https://image.tmdb.org/t/p/w500" + poster,
                        o.optString("release_date").take(4), String.format("%.1f", o.optDouble("vote_average"))))
                }
                films = out
            } catch (_: Exception) { error = "Каталог не загрузился. Проверь подключение и повтори." }
            loading = false
        }
    }

    fun loadArchive() {
        scope.launch {
            loading = true; error = ""
            try {
                val q = URLEncoder.encode("mediatype:movies", "UTF-8")
                val arr = json("https://archive.org/advancedsearch.php?q=" + q + "&fl[]=identifier,title,year,description&rows=40&page=1&output=json")
                    .getJSONObject("response").getJSONArray("docs")
                val out = mutableListOf<Film>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i); val id = o.optString("identifier")
                    if (id.isNotBlank()) out.add(Film(id, o.optString("title", id),
                        o.optString("description", "Материал Internet Archive"),
                        "https://archive.org/services/img/" + id, o.optString("year"), "—",
                        "https://archive.org/details/" + id))
                }
                archive = out
            } catch (_: Exception) { error = "Архив временно недоступен. Попробуй позже." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }
    val data = when (tab) {
        "Избранное" -> films.filter { favorites.contains(it.id) }
        "Архив" -> archive
        else -> films
    }.filter { !ratingFilter || (it.rating.toFloatOrNull() ?: 0f) >= 7f }
        .let { if (sort) it.sortedByDescending { f -> f.rating.toFloatOrNull() ?: 0f } else it }

    MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = PANEL, primary = LIME, secondary = LIME, onPrimary = BG)) {
        Column(Modifier.fillMaxSize().background(BG)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DIOS", color = LIME, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                    Text("CINEMA", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("v1.1.0", color = MUTED, fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Главная", "Фильмы", "Архив", "Избранное").forEach { label ->
                    val active = tab == label
                    Text(label, color = if (active) BG else MUTED, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (active) LIME else PANEL)
                            .clickable { tab = label; if (label == "Архив" && archive.isEmpty()) loadArchive() }
                            .padding(horizontal = 12.dp, vertical = 11.dp))
                }
            }
            if (tab == "Главная" || tab == "Фильмы") {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    placeholder = { Text("Поиск фильмов…", color = MUTED) }, singleLine = true, shape = RoundedCornerShape(15.dp),
                    trailingIcon = { Text("НАЙТИ", color = LIME, modifier = Modifier.clickable { load(query) }.padding(10.dp)) })
                Row(Modifier.padding(horizontal = 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(ratingFilter, { ratingFilter = !ratingFilter }, label = { Text("Рейтинг 7+") })
                    FilterChip(sort, { sort = !sort }, label = { Text("Сначала лучшие") })
                }
            } else Text(if (tab == "Архив") "Коллекция Internet Archive" else "Сохранённые фильмы",
                color = MUTED, modifier = Modifier.padding(16.dp), fontSize = 13.sp)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LIME)
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF9A8D), modifier = Modifier.padding(16.dp))
            if (selected != null) {
                val f = selected!!
                Text("← НАЗАД", color = LIME, modifier = Modifier.clickable { selected = null }.padding(18.dp))
                Text(f.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                Text(f.year + "   ★ " + f.rating, color = LIME, modifier = Modifier.padding(18.dp))
                Text(f.overview, color = MUTED, modifier = Modifier.padding(horizontal = 18.dp))
                Button(onClick = { openLink(if (f.link.isNotBlank()) f.link else "https://www.themoviedb.org/movie/" + f.id) },
                    modifier = Modifier.fillMaxWidth().padding(18.dp), colors = ButtonDefaults.buttonColors(containerColor = LIME)) {
                    Text(if (f.link.isNotBlank()) "ОТКРЫТЬ ПРОСМОТР" else "СТРАНИЦА ФИЛЬМА", color = BG)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    items(data, key = { it.id }) { f ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(PANEL)
                            .clickable { if (tab == "Архив") openLink(f.link) else selected = f }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (f.poster.isNotBlank()) AsyncImage(f.poster, f.title, Modifier.width(82.dp).height(118.dp).clip(RoundedCornerShape(11.dp)))
                            else Box(Modifier.width(82.dp).height(118.dp).background(Color(0xFF303830)), contentAlignment = Alignment.Center) { Text("DIOS", color = LIME) }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(f.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(f.year + "  ★ " + f.rating, color = LIME, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                                Text(f.overview, color = MUTED, fontSize = 11.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                                Text(if (tab == "Архив") "ОТКРЫТЬ В АРХИВЕ" else "ПОДРОБНЕЕ", color = LIME, fontSize = 10.sp,
                                    modifier = Modifier.clickable { if (tab == "Архив") openLink(f.link) else selected = f }.padding(top = 8.dp))
                            }
                            Text(if (favorites.contains(f.id)) "♥" else "♡", color = LIME, fontSize = 22.sp,
                                modifier = Modifier.clickable { if (favorites.contains(f.id)) favorites.remove(f.id) else favorites.add(f.id) }.padding(6.dp))
                        }
                    }
                    if (!loading && data.isEmpty()) item { Text("Пока ничего нет", color = MUTED, modifier = Modifier.padding(22.dp)) }
                }
            }
        }
    }
}
