package com.ssaka.softplayer
import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay

data class Song(val id:Long,val title:String,val artist:String,val uri:Uri,val duration:Long,val filename:String)
class MainActivity:ComponentActivity(){
 private var songs by mutableStateOf<List<Song>>(emptyList())
 private var allowed by mutableStateOf(false)
 private var controller by mutableStateOf<MediaController?>(null)
 private var future:com.google.common.util.concurrent.ListenableFuture<MediaController>?=null
 private val perm=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 private val request=registerForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it;if(it)scan()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);allowed=ContextCompat.checkSelfPermission(this,perm)==PackageManager.PERMISSION_GRANTED;if(allowed)scan()
  future=MediaController.Builder(this,SessionToken(this,ComponentName(this,SoftPlayerPlaybackService::class.java))).buildAsync()
  future?.addListener({try{controller=future?.get()}catch(_:Exception){}},MoreExecutors.directExecutor())
  setContent { AppTheme(content = { PlayerUI(songs, allowed, controller, ask = { request.launch(perm) }) }) }
 }
 private fun scan(){val u=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.DISPLAY_NAME)
  contentResolver.query(u,cols,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,MediaStore.Audio.Media.TITLE+" ASC")?.use{c->
   val id=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val a=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val d=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);val n=c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);val list=mutableListOf<Song>()
   while(c.moveToNext()){val x=c.getLong(id);val file=c.getString(n)?: "";list.add(Song(x,c.getString(t)?:file,c.getString(a)?.takeIf{it!="<unknown>"}?:"Unknown artist",ContentUris.withAppendedId(u,x),c.getLong(d),file))};songs=list
  }
 }
 override fun onDestroy(){future?.let{MediaController.releaseFuture(it)};super.onDestroy()}
}
class SoftPlayerPlaybackService:androidx.media3.session.MediaSessionService(){
 private var session:androidx.media3.session.MediaSession?=null
 override fun onCreate(){super.onCreate();val exo=androidx.media3.exoplayer.ExoPlayer.Builder(this).build();session=androidx.media3.session.MediaSession.Builder(this,exo).build()}
 override fun onGetSession(info:androidx.media3.session.MediaSession.ControllerInfo):androidx.media3.session.MediaSession?=session
 override fun onDestroy(){session?.let{it.player.release();it.release()};session=null;super.onDestroy()}
}
@Composable private fun AppTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFC56BFF), background = Color(0xFF0B0B12), surface = Color(0xFF191624)), content = content) }
private fun format(s:Song)=when(s.filename.substringAfterLast('.', "").lowercase()){"mp3"->"MP3";"wav"->"WAV";"aac","m4a"->"AAC";"ogg","oga"->"OGG";"flac"->"FLAC";"wma"->"WMA";else->"OTHER"}
private fun tm(ms:Long):String{val x=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(x/60,x%60)}
@Composable private fun PlayerUI(songs:List<Song>,allowed:Boolean,p:Player?,ask:()->Unit){
 var tab by remember{mutableStateOf("Library")};var filter by remember{mutableStateOf("ALL")};var playing by remember{mutableStateOf(false)};var idx by remember{mutableIntStateOf(-1)};var pos by remember{mutableLongStateOf(0)};var dur by remember{mutableLongStateOf(0)}
 val song=songs.getOrNull(idx)
 LaunchedEffect(p){while(true){if(p!=null){playing=p.isPlaying;val i=p.currentMediaItemIndex;if(i in songs.indices)idx=i;pos=p.currentPosition.coerceAtLeast(0);dur=p.duration.coerceAtLeast(0)};delay(400)}}
 val play:(Int)->Unit={i->if(p!=null&&i in songs.indices){idx=i;p.setMediaItems(songs.map{MediaItem.fromUri(it.uri)},i,0);p.prepare();p.play();tab="Now Playing"}}
 val next:()->Unit={if(p!=null){if(p.hasNextMediaItem())p.seekToNext()else if(songs.isNotEmpty())play(0)}}
 val prev:()->Unit={if(p!=null){if(p.currentPosition>3000)p.seekTo(0)else if(p.hasPreviousMediaItem())p.seekToPrevious()else if(songs.isNotEmpty())play(songs.lastIndex)}}
 val list=songs.filter{filter=="ALL"||format(it)==filter}
 Column(Modifier.fillMaxSize().background(Color(0xFF0B0B12)).padding(16.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color(0xFF7130B6),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null)};Spacer(Modifier.width(10.dp));Column{Text("SOFT PLAYER",fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("Your music. Your mood.",fontSize=12.sp,color=Color.Gray)};Spacer(Modifier.weight(1f));Icon(Icons.Default.GraphicEq,null,tint=Color(0xFFC56BFF))}
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.padding(top=12.dp)){FilterChip(tab=="Library",{tab="Library"},label={Text("Library")});FilterChip(tab=="Now Playing",{tab="Now Playing"},label={Text("Now Playing")})}
  if(tab=="Library"){
   Text("${songs.size} songs",fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=8.dp))
   Row(horizontalArrangement=Arrangement.spacedBy(2.dp)){listOf("ALL","MP3","WAV","AAC","OGG","FLAC","OTHER").forEach{f->FilterChip(filter==f,{filter=f},label={Text(f,fontSize=10.sp)},modifier=Modifier.weight(1f))}}
   if(!allowed){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Allow audio access");Button(onClick=ask){Text("Grant access")}}}}
   else if(list.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(songs.isEmpty())"No music found" else "No $filter files",color=Color.Gray)}}
   else{LazyColumn(Modifier.weight(1f)){items(list,key={it.id}){s->Row(Modifier.fillMaxWidth().clickable{play(songs.indexOf(s))}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(44.dp).background(Color(0xFF211A30),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=Color(0xFFC56BFF))};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(s.title,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${s.artist} • ${format(s)}",fontSize=12.sp,color=Color.Gray)};Text(tm(s.duration),fontSize=12.sp,color=Color.Gray)};HorizontalDivider()}}}
  }else{
   if(song==null){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("Select a song from Library",color=Color.Gray)}}
   else{Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Disc(playing);Spacer(Modifier.height(20.dp));Text(song.title,fontSize=21.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Text(song.artist,color=Color.Gray);Text(format(song),color=Color(0xFFC56BFF),fontSize=12.sp);Slider(value=if(dur>0)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={pos=it.toLong()},onValueChangeFinished={p?.seekTo(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat());Row{Text(tm(pos),fontSize=12.sp,color=Color.Gray);Spacer(Modifier.weight(1f));Text(tm(dur),fontSize=12.sp,color=Color.Gray)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){IconButton(onClick=prev){Icon(Icons.Default.SkipPrevious,null,Modifier.size(36.dp))};IconButton(onClick={if(playing)p?.pause()else p?.play()},Modifier.size(56.dp).background(Color(0xFF7D28C8),CircleShape)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,Modifier.size(32.dp))};IconButton(onClick=next){Icon(Icons.Default.SkipNext,null,Modifier.size(36.dp))};IconButton(onClick={p?.stop();idx=-1;pos=0;tab="Library"}){Icon(Icons.Default.Stop,null,Modifier.size(32.dp))}}
    Text("Background playback enabled",fontSize=12.sp,color=Color.Gray,modifier=Modifier.padding(top=14.dp))}}
  }
  if(song!=null&&tab=="Library"){Row(Modifier.fillMaxWidth().background(Color(0xFF191624),RoundedCornerShape(14.dp)).clickable{tab="Now Playing"}.padding(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Album,null,tint=Color(0xFFC56BFF));Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(song.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold);Text(song.artist,fontSize=11.sp,color=Color.Gray)};IconButton(onClick={if(playing)p?.pause()else p?.play()}){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null)};IconButton(onClick=next){Icon(Icons.Default.SkipNext,null)}}
  }
 }
}
@Composable private fun Disc(playing:Boolean){val rotation=remember{Animatable(0f)};LaunchedEffect(playing){while(playing){rotation.animateTo(rotation.value+360f,tween(5000,easing=LinearEasing))}};Box(Modifier.size(245.dp).rotate(rotation.value).background(Color(0xFF24212D),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(215.dp).background(Color(0xFF111117),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(175.dp).background(Color(0xFF292431),CircleShape),contentAlignment=Alignment.Center){Box(Modifier.size(110.dp).background(Color(0xFF7D28C8),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Default.Headphones,null,Modifier.size(50.dp),tint=Color.White)}}};Box(Modifier.size(8.dp).background(Color.Black,CircleShape))}}
