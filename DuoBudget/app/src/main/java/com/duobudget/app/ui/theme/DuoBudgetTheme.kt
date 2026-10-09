package com.duobudget.app.ui.theme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
private val Light=lightColorScheme(primary=Color(0xFF0D665B),secondary=Color(0xFF48665D),background=Color(0xFFF5F8F7),surface=Color(0xFFFCFEFD),surfaceContainer=Color(0xFFEAF1EE),surfaceVariant=Color(0xFFE2EDE7),error=Color(0xFFBA1A1A))
private val Dark=darkColorScheme(primary=Color(0xFF90D5C5),secondary=Color(0xFFACCFC3),background=Color(0xFF101815),surface=Color(0xFF15201B),surfaceContainer=Color(0xFF1C2B24),surfaceVariant=Color(0xFF2B3F35))
@Composable fun DuoBudgetTheme(darkTheme:Boolean,content:@Composable ()->Unit){MaterialTheme(colorScheme=if(darkTheme)Dark else Light,content=content)}
