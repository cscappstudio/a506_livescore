package com.football.livescores.matchlive.data.remote

import com.football.livescores.matchlive.data.crypto.CryptoUtils
import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject

class DecryptionInterceptor(
    private val param1: String = "1",
    private val param2: String = "1",
    private val secretParam3: String = "Live_Lcore"
) : Interceptor {

    private val passphrase = "${param1}__${param2}__$secretParam3"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        
        val urlPath = request.url.encodedPath
        val isClientApi = urlPath.contains("/api/v1/")
        val isDebugMode = request.url.queryParameter("param1") == "1" && 
                          request.url.queryParameter("param2") == "1"
        
        Log.d("DecryptionInterceptor", "Intercepted: $urlPath, code: ${response.code}, isClientApi: $isClientApi")
        
        if (!response.isSuccessful || !isClientApi) {
            Log.d("DecryptionInterceptor", "Bypassing decryption for: $urlPath (code=${response.code}, isClientApi=$isClientApi)")
            return response
        }

        val responseBody = response.body
        if (responseBody != null) {
            val rawBodyString = responseBody.string()
            try {
                val jsonEnvelope = JSONObject(rawBodyString)
                
                // Kiểm tra xem cấu trúc response có phải là vỏ bọc chuẩn và có data hay không
                if (jsonEnvelope.has("code") && jsonEnvelope.has("data")) {
                    val code = jsonEnvelope.getInt("code")
                    val dataObj = jsonEnvelope.opt("data")
                    
                    Log.d("DecryptionInterceptor", "Envelope found for $urlPath, code: $code, data is String: ${dataObj is String}, isDebugMode: $isDebugMode")
                    
                    if (code == 200 && dataObj != null && dataObj != JSONObject.NULL) {
                        val trimmed = if (dataObj is String) {
                            if (isDebugMode && (dataObj.trim().startsWith("{") || dataObj.trim().startsWith("["))) {
                                dataObj.trim()
                            } else {
                                // Thực hiện giải mã
                                Log.d("DecryptionInterceptor", "Attempting decryption for $urlPath with passphrase: '$passphrase'")
                                CryptoUtils.decryptAES256(dataObj, passphrase).trim()
                            }
                        } else {
                            dataObj.toString().trim()
                        }
                        
                        // Thực hiện mapping dữ liệu tùy theo API để tương thích với DTO của App
                        val mappedData = mapDecryptedData(urlPath, trimmed)
                        
                        // Thay thế trường "data" bằng JSON Object / Array đã giải mã & mapped thực tế
                        val finalJsonObject = JSONObject().apply {
                            put("code", code)
                            put("message", jsonEnvelope.optString("message", ""))
                            put("data", mappedData)
                        }
                        
                        val newResponseBodyString = finalJsonObject.toString()
                        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                        
                        Log.d("DecryptionInterceptor", "Decryption and mapping successful for $urlPath")
                        
                        return response.newBuilder()
                            .body(newResponseBodyString.toResponseBody(mediaType))
                            .build()
                    } else {
                        Log.w("DecryptionInterceptor", "Envelope code not 200 or data is null: code=$code")
                    }
                } else {
                    Log.w("DecryptionInterceptor", "Response envelope lacks code or data fields: $rawBodyString")
                }
            } catch (e: Exception) {
                Log.e("DecryptionInterceptor", "Decryption/Parsing failed for $urlPath. Raw response: $rawBodyString", e)
            }
            val mediaType = responseBody.contentType() ?: "application/json; charset=utf-8".toMediaTypeOrNull()
            return response.newBuilder()
                .body(rawBodyString.toResponseBody(mediaType))
                .build()
        } else {
            Log.w("DecryptionInterceptor", "Response body is null for $urlPath")
        }
        
        return response
    }

    /**
     * Chuyển đổi dữ liệu thô sau khi giải mã sang đúng định dạng cấu trúc cũ mà App mong đợi.
     */
    private fun mapDecryptedData(urlPath: String, decryptedText: String): Any {
        return when {
            // 1. API: /api/v1/leagues
            urlPath.contains("/leagues") && !urlPath.contains("/standings") && !urlPath.contains("/topscorers") && !urlPath.contains("/topassists") -> {
                val originalArray = JSONArray(decryptedText)
                val newArray = JSONArray()
                for (i in 0 until originalArray.length()) {
                    val item = originalArray.getJSONObject(i)
                    val newItem = JSONObject(item.toString()) // sao chép
                    
                    // Xử lý "country" (Chuyển từ String sang Object { name, code, flag })
                    if (item.has("country") && item.opt("country") !is JSONObject) {
                        val countryStr = item.optString("country", "")
                        newItem.put("country", JSONObject().apply {
                            put("name", countryStr)
                            put("code", null)
                            put("flag", null)
                        })
                    }
                    
                    // Thêm các trường mặc định nếu thiếu để tránh lỗi Gson không tìm thấy
                    if (!newItem.has("_id")) newItem.put("_id", "")
                    if (!newItem.has("is_popular")) newItem.put("is_popular", false)
                    if (!newItem.has("sync_priority")) newItem.put("sync_priority", "Normal")
                    if (!newItem.has("live_detail_ttl_seconds")) newItem.put("live_detail_ttl_seconds", 300)
                    if (!newItem.has("created_at")) newItem.put("created_at", "")
                    if (!newItem.has("updated_at")) newItem.put("updated_at", "")
                    
                    newArray.put(newItem)
                }
                newArray
            }

            // 2. API: /api/v1/leagues/{id}/standings
            urlPath.contains("/standings") -> {
                println("DEBUG STANDINGS DECRYPTED TEXT: $decryptedText")
                val originalObj = JSONObject(decryptedText)
                val standingsArray = originalObj.optJSONArray("standings") ?: JSONArray()
                
                var currentGroupChar = 'A'
                var groupCount = 0
                
                val mappedStandings = JSONArray()
                for (i in 0 until standingsArray.length()) {
                    val row = standingsArray.getJSONObject(i)
                    val mappedRow = JSONObject().apply {
                        val rank = row.optInt("rank", 0)
                        put("rank", rank)
                        put("points", row.optInt("points", 0))
                        
                        val goalsDiff = if (row.has("goalsDiff")) {
                            row.optInt("goalsDiff", 0)
                        } else {
                            row.optInt("goals_diff", 0)
                        }
                        put("goalsDiff", goalsDiff)
                        put("form", row.optString("form", null))

                        // Trích xuất group name một cách linh hoạt
                        val groupVal = when {
                            row.has("group") && !row.isNull("group") -> {
                                val groupObj = row.optJSONObject("group")
                                if (groupObj != null) {
                                    groupObj.optString("name", null)
                                        ?: groupObj.optString("group_name", null)
                                        ?: groupObj.optString("groupName", null)
                                        ?: row.optString("group", null)
                                } else {
                                    row.optString("group", null)
                                }
                            }
                            row.has("group_name") && !row.isNull("group_name") -> row.optString("group_name", null)
                            row.has("groupName") && !row.isNull("groupName") -> row.optString("groupName", null)
                            else -> null
                        }

                        var cleanGroupVal = if (groupVal == "null" || groupVal.isNullOrEmpty()) null else groupVal
                        
                        if (cleanGroupVal == null) {
                            // Tính toán group động dựa trên rank resets (cho WC)
                            if (rank == 1 && i > 0) {
                                groupCount++
                                currentGroupChar = ('A'.code + groupCount).toChar()
                            }
                            cleanGroupVal = if (groupCount >= 12) {
                                "3rd Place Teams"
                            } else {
                                "Group $currentGroupChar"
                            }
                        }

                        Log.d("DecryptionInterceptor", "Mapping standings row: team=${row.optJSONObject("team")?.optString("name")}, rank=$rank, group=$cleanGroupVal")

                        put("group", cleanGroupVal)
                        put("status", row.optString("status", null))
                        put("description", row.optString("description", null))
                        
                        // Map team
                        put("team", mapTeam(row.optJSONObject("team")))
                        
                        // Map "all", "home", "away": played, win, draw, lose, goals
                        val rawAll = row.optJSONObject("all")
                        val rawHome = row.optJSONObject("home") ?: rawAll
                        val rawAway = row.optJSONObject("away") ?: rawAll
                        
                        put("all", mapStatGroup(rawAll))
                        put("home", mapStatGroup(rawHome))
                        put("away", mapStatGroup(rawAway))
                    }
                    mappedStandings.put(mappedRow)
                }

                // Trả về danh sách bọc StandingsLeagueWrapperDto
                val newArray = JSONArray()
                val rawLeague = originalObj.optJSONObject("league")
                val rawCountryObj = rawLeague?.optJSONObject("country")
                val wrapper = JSONObject().apply {
                    put("league", JSONObject().apply {
                        put("id", rawLeague?.optInt("id", 0) ?: originalObj.optInt("league_id", 0))
                        put("name", rawLeague?.optString("name") ?: originalObj.optString("league_name", ""))
                        put("country", rawCountryObj?.optString("name") ?: rawLeague?.optString("country") ?: originalObj.optString("league_country", ""))
                        put("logo", rawLeague?.optString("logo") ?: originalObj.optString("league_logo", ""))
                        put("flag", rawCountryObj?.optString("flag", null) ?: rawLeague?.optString("flag") ?: originalObj.optString("league_flag", null))
                        val rawSeason = rawLeague?.optInt("season", 0) ?: originalObj.optInt("season", 0)
                        put("season", if (rawSeason > 0) rawSeason else 2026)
                        // Cấu hình standings dạng List<List<StandingRowDto>>
                        put("standings", JSONArray().put(mappedStandings))
                    })
                }
                newArray.put(wrapper)
                newArray
            }

            // 3. API: /api/v1/leagues/{id}/topscorers hoặc topassists
            urlPath.contains("/topscorers") || urlPath.contains("/topassists") -> {
                val originalObj = JSONObject(decryptedText)
                val playersArray = originalObj.optJSONArray("players") ?: JSONArray()

                val newArray = JSONArray()
                for (i in 0 until playersArray.length()) {
                    val playerItem = playersArray.getJSONObject(i)
                    val mappedItem = JSONObject().apply {
                        put("player", mapPlayer(playerItem.optJSONObject("player")) ?: mapPlayer(playerItem))
                        put("statistics", JSONArray().put(JSONObject().apply {
                            put("team", mapTeam(playerItem.optJSONObject("team")))
                            put("goals", JSONObject().apply {
                                put("total", playerItem.optInt("goals", 0))
                                put("assists", playerItem.optInt("assists", 0))
                            })
                        }))
                    }
                    newArray.put(mappedItem)
                }
                newArray
            }

            // API: /api/v1/fixtures/bracket
            urlPath.contains("/bracket") -> {
                val responseObj = if (decryptedText.trim().startsWith("{")) {
                    JSONObject(decryptedText)
                } else {
                    JSONObject()
                }
                
                val dataObj = responseObj.optJSONObject("data") ?: responseObj
                val roundsArray = dataObj.optJSONArray("rounds") ?: JSONArray()
                val newArray = JSONArray()
                
                for (r in 0 until roundsArray.length()) {
                    val roundObj = roundsArray.getJSONObject(r)
                    val roundName = roundObj.optString("round_name", "")
                    val fixturesArray = roundObj.optJSONArray("fixtures") ?: JSONArray()
                    
                    for (f in 0 until fixturesArray.length()) {
                        val fObj = fixturesArray.getJSONObject(f)
                        val mappedMatch = JSONObject().apply {
                            put("fixture", JSONObject().apply {
                                put("id", fObj.optInt("fixture_id", 0))
                                put("referee", null)
                                put("timezone", "UTC")
                                val dateStr = fObj.optString("date", "")
                                put("date", dateStr)
                                put("timestamp", parseDateToTimestamp(dateStr))
                                put("status", JSONObject().apply {
                                    put("short", fObj.optString("status", "NS"))
                                    put("long", fObj.optString("status_long", "Not Started"))
                                    put("elapsed", if (fObj.isNull("elapsed")) null else fObj.optInt("elapsed"))
                                })
                            })
                            put("league", JSONObject().apply {
                                put("id", 1)
                                put("name", "World Cup")
                                put("country", "World")
                                put("logo", "https://media.api-sports.io/football/leagues/1.png")
                                put("flag", null)
                                put("season", 2026)
                                put("round", roundName)
                            })
                            
                            val teamsObj = fObj.optJSONObject("teams")
                            put("teams", JSONObject().apply {
                                val homeObj = teamsObj?.optJSONObject("home")
                                val awayObj = teamsObj?.optJSONObject("away")
                                put("home", JSONObject().apply {
                                    put("id", homeObj?.optInt("id", 0) ?: 0)
                                    put("name", homeObj?.optString("name", "TBD") ?: "TBD")
                                    put("logo", homeObj?.optString("logo", "") ?: "")
                                    put("winner", homeObj?.opt("winner"))
                                })
                                put("away", JSONObject().apply {
                                    put("id", awayObj?.optInt("id", 0) ?: 0)
                                    put("name", awayObj?.optString("name", "TBD") ?: "TBD")
                                    put("logo", awayObj?.optString("logo", "") ?: "")
                                    put("winner", awayObj?.opt("winner"))
                                })
                            })
                            
                            put("goals", fObj.optJSONObject("goals"))
                            put("score", fObj.optJSONObject("score"))
                        }
                        newArray.put(mappedMatch)
                    }
                }
                newArray
            }

            // API: /api/v1/fixtures/rounds
            urlPath.contains("/rounds") -> {
                if (decryptedText.trim().startsWith("{")) {
                    JSONObject(decryptedText).optJSONArray("response") ?: JSONArray()
                } else {
                    JSONArray(decryptedText)
                }
            }

            // API: /api/v1/teams/{team_id}/fixtures
            urlPath.contains("/teams/") && urlPath.contains("/fixtures") -> {
                JSONObject(decryptedText)
            }

            // 4. API: /api/v1/fixtures, date hoặc live
            (urlPath.contains("/fixtures") && !urlPath.contains("/teams/") && !urlPath.contains("/details") && !urlPath.contains("/ai-prediction") && !urlPath.contains("/rounds") && !urlPath.contains("/bracket")) -> {
                val originalArray = if (decryptedText.trim().startsWith("{")) {
                    JSONObject(decryptedText).optJSONArray("response") ?: JSONArray()
                } else {
                    JSONArray(decryptedText)
                }
                val newArray = JSONArray()
                for (i in 0 until originalArray.length()) {
                    val match = originalArray.getJSONObject(i)
                    val rawFixture = match.optJSONObject("fixture")
                    val rawLeague = match.optJSONObject("league")
                    val rawCountryObj = rawLeague?.optJSONObject("country")
                    val mappedMatch = JSONObject().apply {
                        put("fixture", JSONObject().apply {
                            put("id", rawFixture?.optInt("id", 0) ?: match.optInt("fixture_id", 0))
                            put("referee", rawFixture?.optString("referee", null) ?: match.optString("referee", null))
                            put("timezone", rawFixture?.optString("timezone", "UTC") ?: match.optString("timezone", "UTC"))
                            val dateStr = rawFixture?.optString("date") ?: match.optString("date", "")
                            put("date", dateStr)
                            val rawTimestamp = rawFixture?.optLong("timestamp", 0L) ?: match.optLong("dateTimestamp", 0L)
                            val finalTimestamp = if (rawTimestamp > 0L) {
                                rawTimestamp
                            } else {
                                val parsed = parseDateToTimestamp(dateStr)
                                if (parsed > 0L) parsed else rawTimestamp
                            }
                            put("timestamp", finalTimestamp)
                            put("status", rawFixture?.optJSONObject("status") ?: match.optJSONObject("status"))
                        })
                        put("league", JSONObject().apply {
                            put("id", rawLeague?.optInt("id", 0) ?: match.optInt("league_id", 0))
                            put("name", rawLeague?.optString("name") ?: match.optString("league_name", ""))
                            put("country", rawCountryObj?.optString("name") ?: rawLeague?.optString("country") ?: match.optString("league_country", ""))
                            put("logo", rawLeague?.optString("logo") ?: match.optString("league_logo", ""))
                            put("flag", rawCountryObj?.optString("flag", null) ?: rawLeague?.optString("flag") ?: match.optString("league_flag", null))
                            val rawSeason = rawLeague?.optInt("season", 0) ?: match.optInt("season", 0)
                            put("season", if (rawSeason > 0) rawSeason else 2026)
                            put("round", rawLeague?.optString("round") ?: match.optString("round", match.optString("league_round", null)))
                        })
                        put("teams", mapTeamsContainer(match.optJSONObject("teams")))
                        put("goals", match.optJSONObject("goals"))
                        put("score", match.optJSONObject("score"))
                    }
                    newArray.put(mappedMatch)
                }
                newArray
            }

            // 5. API: /api/v1/fixtures/{id}/details
            urlPath.contains("/details") -> {
                val originalObj = JSONObject(decryptedText)
                val fixtureObj = originalObj.optJSONObject("fixture")
                val detailsObj = originalObj.optJSONObject("details")

                // Map stats
                val rawStats = detailsObj?.optJSONArray("statistics") ?: JSONArray()
                val mappedStats = JSONArray()
                for (i in 0 until rawStats.length()) {
                    val statEntry = rawStats.getJSONObject(i)
                    mappedStats.put(JSONObject().apply {
                        put("team", mapTeam(statEntry.optJSONObject("team")))
                        put("statistics", statEntry.optJSONArray("statistics") ?: JSONArray())
                    })
                }

                // Map events
                val rawEvents = detailsObj?.optJSONArray("events") ?: JSONArray()
                val mappedEvents = JSONArray()
                for (i in 0 until rawEvents.length()) {
                    val event = rawEvents.getJSONObject(i)
                    mappedEvents.put(JSONObject().apply {
                        put("time", event.optJSONObject("time"))
                        put("team", mapTeam(event.optJSONObject("team")))
                        put("player", mapPlayer(event.optJSONObject("player")))
                        put("assist", mapPlayer(event.optJSONObject("assist")) ?: JSONObject.NULL)
                        put("type", event.optString("type", ""))
                        put("detail", event.optString("detail", ""))
                        val comments = if (event.isNull("comments")) null else event.optString("comments")
                        put("comments", comments ?: JSONObject.NULL)
                    })
                }

                // Map lineups
                val rawLineups = detailsObj?.optJSONArray("lineups") ?: JSONArray()
                val mappedLineups = JSONArray()
                for (i in 0 until rawLineups.length()) {
                    val lineup = rawLineups.getJSONObject(i)
                    mappedLineups.put(JSONObject().apply {
                        put("team", mapTeam(lineup.optJSONObject("team")))
                        put("coach", mapCoach(lineup.optJSONObject("coach")))
                        put("formation", lineup.optString("formation", ""))
                        put("startXI", mapLineupPlayers(lineup.optJSONArray("startXI")))
                        put("substitutes", mapLineupPlayers(lineup.optJSONArray("substitutes")))
                    })
                }

                // MatchDetailDto bọc ngoài cùng là mảng có 1 phần tử
                val newArray = JSONArray()
                val rawLeague = originalObj.optJSONObject("league") ?: fixtureObj?.optJSONObject("league")
                val rawCountryObj = rawLeague?.optJSONObject("country")
                val rawTeams = fixtureObj?.optJSONObject("teams") ?: originalObj.optJSONObject("teams")
                val rawGoals = fixtureObj?.optJSONObject("goals") ?: originalObj.optJSONObject("goals")
                val rawScore = fixtureObj?.optJSONObject("score") ?: detailsObj?.optJSONObject("score") ?: originalObj.optJSONObject("score")
                val detailObject = JSONObject().apply {
                    put("fixture", JSONObject().apply {
                        put("id", fixtureObj?.optInt("fixture_id", 0) ?: fixtureObj?.optInt("id", 0) ?: 0)
                        put("referee", fixtureObj?.optString("referee", null))
                        put("timezone", fixtureObj?.optString("timezone", "UTC") ?: "UTC")
                        val dateStr = fixtureObj?.optString("date", "")
                        put("date", dateStr)
                        val rawTimestamp = fixtureObj?.optLong("timestamp", 0L) ?: fixtureObj?.optLong("dateTimestamp", 0L) ?: 0L
                        val finalTimestamp = if (rawTimestamp > 0L) {
                            rawTimestamp
                        } else {
                            val parsed = parseDateToTimestamp(dateStr)
                            if (parsed > 0L) parsed else rawTimestamp
                        }
                        put("timestamp", finalTimestamp)
                        put("status", fixtureObj?.optJSONObject("status"))
                    })
                    put("league", JSONObject().apply {
                        val leagueId = rawLeague?.optInt("id", 0)
                            ?: fixtureObj?.optInt("league_id", 0)
                            ?: originalObj.optInt("league_id", 0)
                        put("id", leagueId)
                        put("name", rawLeague?.optString("name") ?: originalObj.optString("league_name", ""))
                        put("country", rawCountryObj?.optString("name") ?: rawLeague?.optString("country") ?: originalObj.optString("league_country", ""))
                        put("logo", rawLeague?.optString("logo") ?: originalObj.optString("league_logo", ""))
                        put("flag", rawCountryObj?.optString("flag", null) ?: rawLeague?.optString("flag") ?: originalObj.optString("league_flag", null))
                        val rawSeason = rawLeague?.optInt("season", 0) ?: originalObj.optInt("season", 0)
                        put("season", if (rawSeason > 0) rawSeason else 2026)
                        put("round", rawLeague?.optString("round") ?: originalObj.optString("round", originalObj.optString("league_round", null)))
                    })
                    put("teams", mapTeamsContainer(rawTeams))
                    put("goals", rawGoals ?: JSONObject().apply {
                        put("home", JSONObject.NULL)
                        put("away", JSONObject.NULL)
                    })
                    put("score", rawScore)
                    put("statistics", mappedStats)
                    put("events", mappedEvents)
                    put("lineups", mappedLineups)
                }
                newArray.put(detailObject)
                newArray
            }

            // 6. API: /api/v1/users/register
            urlPath.contains("/users/register") -> {
                val originalObj = JSONObject(decryptedText)
                val newArray = JSONArray()
                newArray.put(originalObj)
                newArray
            }

            // Mặc định trả về định dạng ban đầu của JSON thô
            else -> {
                if (decryptedText.startsWith("[")) {
                    JSONArray(decryptedText)
                } else {
                    JSONObject(decryptedText)
                }
            }
        }
    }

    private fun mapTeam(teamObj: JSONObject?): JSONObject? {
        if (teamObj == null) return null
        return JSONObject().apply {
            put("id", if (teamObj.has("id")) teamObj.optInt("id") else teamObj.optInt("api_id", 0))
            put("name", teamObj.optString("name", ""))
            put("logo", teamObj.optString("logo", ""))
            put("winner", teamObj.opt("winner"))
        }
    }

    private fun mapStatGroup(statObj: JSONObject?): JSONObject {
        if (statObj == null) {
            return JSONObject().apply {
                put("played", 0)
                put("win", 0)
                put("draw", 0)
                put("lose", 0)
                put("goals", JSONObject().apply {
                    put("for", 0)
                    put("against", 0)
                })
            }
        }
        return JSONObject().apply {
            put("played", statObj.optInt("played", 0))
            put("win", statObj.optInt("win", 0))
            put("draw", statObj.optInt("draw", 0))
            put("lose", statObj.optInt("lose", 0))
            
            val goals = statObj.optJSONObject("goals")
            put("goals", JSONObject().apply {
                put("for", goals?.optInt("for", 0) ?: statObj.optInt("goals_for", 0))
                put("against", goals?.optInt("against", 0) ?: statObj.optInt("goals_against", 0))
            })
        }
    }

    private fun mapTeamsContainer(teamsObj: JSONObject?): JSONObject {
        if (teamsObj == null) {
            return JSONObject().apply {
                put("home", JSONObject().apply { put("id", 0); put("name", ""); put("logo", "") })
                put("away", JSONObject().apply { put("id", 0); put("name", ""); put("logo", "") })
            }
        }
        val homeObj = teamsObj.optJSONObject("home")
        val awayObj = teamsObj.optJSONObject("away")
        return JSONObject().apply {
            put("home", JSONObject().apply {
                put("id", homeObj?.let { if (it.has("id")) it.optInt("id") else it.optInt("api_id", 0) } ?: 0)
                put("name", homeObj?.optString("name", "") ?: "")
                put("logo", homeObj?.optString("logo", "") ?: "")
                put("winner", homeObj?.opt("winner"))
            })
            put("away", JSONObject().apply {
                put("id", awayObj?.let { if (it.has("id")) it.optInt("id") else it.optInt("api_id", 0) } ?: 0)
                put("name", awayObj?.optString("name", "") ?: "")
                put("logo", awayObj?.optString("logo", "") ?: "")
                put("winner", awayObj?.opt("winner"))
            })
        }
    }

    private fun mapPlayer(playerObj: JSONObject?): JSONObject? {
        if (playerObj == null) return null
        return JSONObject().apply {
            put("id", if (playerObj.has("id")) playerObj.optInt("id") else playerObj.optInt("api_id", 0))
            put("name", playerObj.optString("name", ""))
            put("firstname", playerObj.optString("firstname", ""))
            put("lastname", playerObj.optString("lastname", ""))
            put("photo", playerObj.optString("photo", ""))
            put("nationality", playerObj.optString("nationality", ""))
            put("age", playerObj.optInt("age", 0))
        }
    }

    private fun mapCoach(coachObj: JSONObject?): JSONObject? {
        if (coachObj == null) return null
        return JSONObject().apply {
            put("id", if (coachObj.has("id")) coachObj.optInt("id") else coachObj.optInt("api_id", 0))
            put("name", coachObj.optString("name", ""))
            put("photo", coachObj.optString("photo", ""))
        }
    }

    private fun mapLineupPlayers(list: JSONArray?): JSONArray {
        val mappedList = JSONArray()
        if (list == null) return mappedList
        for (j in 0 until list.length()) {
            val wrapper = list.getJSONObject(j)
            val player = wrapper.optJSONObject("player")
            if (player != null) {
                val pId = if (player.has("id")) player.optInt("id") else player.optInt("api_id", 0)
                val mappedPlayer = JSONObject().apply {
                    put("id", pId)
                    put("name", player.optString("name", ""))
                    put("number", player.optInt("number", 0))
                    put("pos", player.optString("pos", null))
                    put("grid", player.optString("grid", null))
                }
                mappedList.put(JSONObject().apply { put("player", mappedPlayer) })
            }
        }
        return mappedList
    }

    private fun parseDateToTimestamp(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        
        // 0. Try ISO 8601 offset format with milliseconds: "2026-06-02T17:00:00.000+00:00" or "2026-06-02T17:00:00.000Z"
        try {
            val cleanStr = dateStr.replace("Z", "+00:00")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
            val date = sdf.parse(cleanStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        // 1. Try standard ISO 8601 offset format: "2026-06-05T00:00:00+00:00"
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val date = sdf.parse(dateStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        // 2. Try ISO 8601 offset format without colons in offset: "2026-06-05T00:00:00+0000" or similar
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX", java.util.Locale.US)
            val date = sdf.parse(dateStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        // 3. Try with Z (UTC indicator): "2026-06-05T00:00:00Z"
        try {
            val cleanStr = dateStr.replace("Z", "+00:00")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
            val date = sdf.parse(cleanStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        // 4. Try format without offset: "2026-06-05T00:00:00" (assume UTC)
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(dateStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        // 5. Try simple date format: "2026-06-05" (assume UTC)
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = sdf.parse(dateStr)
            if (date != null) return date.time / 1000L
        } catch (e: Exception) {
            // ignore
        }

        return 0L
    }
}
