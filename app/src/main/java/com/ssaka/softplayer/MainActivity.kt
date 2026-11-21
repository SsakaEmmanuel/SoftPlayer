package com.ssaka.softplayer
import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

data class Song(val id:Long,val title:String,val artist:String,val uri:Uri,val duration:Long)
class MainActivity:ComponentActivity(){
 private lateinit var player:ExoPlayer
 private var songs by mutableStateOf<List<Song>>(emptyList())
 private var granted by mutableStateOf(false)
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ok->granted=ok;if(ok)scan()}
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);player=ExoPlayer.Builder(this).build();granted=hasPermission();if(granted)scan();setContent{Theme{Screen(songs,granted,player){permission.launch(audioPermission())}}}}
 private fun audioPermission()=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
 private fun hasPermission()=ContextCompat.checkSelfPermission(this,audioPermission())==PackageManager.PERMISSION_GRANTED
 private fun scan(){val uri=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;val cols=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION);val c=contentResolver.query(uri,cols,"${MediaStore.Audio.Media.IS_MUSIC} != 0",null,MediaStore.Audio.Media.TITLE+" ASC");val out=mutableListOf<Song>();c?.use{cur->val id=cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);val t=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);val a=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);val d=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);while(cur.moveToNext()){val n=cur.getLong(id);out.add(Song(n,cur.getString(t)?:"Unknown track",cur.getString(a)?.takeIf{it!="<unknown>"}?:"Unknown artist",ContentUris.withAppendedId(uri,n),cur.getLong(d)))}};songs=out}
 override fun onDestroy(){player.release();super.onDestroy()}
}
@Composable private fun Theme(content:@Composable()->Unit){MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFFB5F36B),background=Color(0xFF10120F),surface=Color(0xFF1A1D18),onPrimary=Color(0xFF17200D)),content=content)}
@Composable private fun Screen(songs:List<Song>,allowed:Boolean,p:ExoPlayer,ask:()->Unit){
 var selected by remember{mutableStateOf<Song?>(null)};var playing by remember{mutableStateOf(false)};var sh by remember{mutableStateOf(false)};var rep by remember{mutableIntStateOf(Player.REPEAT_MODE_OFF)};var pos by remember{mutableLongStateOf(0L)};var dur by remember{mutableLongStateOf(0L)}
 DisposableEffect(p){val l=object:Player.Listener{override fun onIsPlayingChanged(v:Boolean){playing=v};override fun onMediaItemTransition(m:MediaItem?,r:Int){selected=songs.getOrNull(p.currentMediaItemIndex)}};p.addListener(l);onDispose{p.removeListener(l)}}
 LaunchedEffect(p){while(true){pos=p.currentPosition.coerceAtLeast(0);dur=p.duration.coerceAtLeast(0);delay(500)}}
 fun play(s:Song){selected=s;p.setMediaItems(songs.map{MediaItem.fromUri(it.uri)},songs.indexOf(s),0);p.prepare();p.play()}
 fun next(){if(p.hasNextMediaItem())p.seekToNext()else if(songs.isNotEmpty())play(songs.first())}
 fun prev(){if(p.currentPosition>3000)p.seekTo(0)else if(p.hasPreviousMediaItem())p.seekToPrevious()else if(songs.isNotEmpty())play(songs.last())}
 Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal=18.dp)){
  Spacer(Modifier.height(22.dp));Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.GraphicEq,null,tint=Color.Black)};Spacer(Modifier.width(10.dp));Column{Text("SOFT PLAYER",fontWeight=FontWeight.Black,letterSpacing=2.sp);Text("Your music. Your mood.",fontSize=12.sp,color=Color.Gray)};Spacer(Modifier.weight(1f));Icon(Icons.Default.Headphones,null,tint=MaterialTheme.colorScheme.primary)}
  Spacer(Modifier.height(24.dp));Text("YOUR LIBRARY",fontSize=11.sp,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold,letterSpacing=2.sp);Text("${songs.size} songs",fontSize=28.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp))
  if(!allowed){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.Center,verticalArrangement=Arrangement.spacedBy(12.dp)){Icon(Icons.Default.LibraryMusic,null,Modifier.size(60.dp),tint=MaterialTheme.colorScheme.primary);Text("Find music on your phone",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Allow access to audio files.",color=Color.Gray);Button(onClick=ask){Text("Allow music access")}}}}
  else if(songs.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.Center){Icon(Icons.Default.MusicOff,null,Modifier.size(56.dp),tint=Color.Gray);Text("No music found",fontWeight=FontWeight.Bold);Text("Add audio files to your phone.",color=Color.Gray)}}}
  else{LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=10.dp)){items(songs,key={it.id}){s->Row(Modifier.fillMaxWidth().clickable{play(s)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(46.dp).background(Color(0xFF272B24),RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.MusicNote,null,tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(s.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium);Text(s.artist,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=12.sp,color=Color.Gray)};Text(fmt(s.duration),fontSize=12.sp,color=Color.Gray)};HorizontalDivider(color=Color(0xFF292D26))}}}
  if(selected!=null){Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(topStart=20.dp,topEnd=20.dp)).padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).background(Color(0xFF30372A),RoundedCornerShape(11.dp)),contentAlignment=Alignment.Center){Icon(Icons.Default.Album,null,tint=MaterialTheme.colorScheme.primary)};Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(selected!!.title,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold);Text(selected!!.artist,color=Color.Gray,fontSize=12.sp)};IconButton(onClick={sh=!sh;p.shuffleModeEnabled=sh}){Icon(Icons.Default.Shuffle,null,tint=if(sh)MaterialTheme.colorScheme.primary else Color.Gray)};IconButton(onClick={rep=when(rep){0->Player.REPEAT_MODE_ALL;Player.REPEAT_MODE_ALL->Player.REPEAT_MODE_ONE;else->0};p.repeatMode=rep}){Icon(Icons.Default.Repeat,null,tint=if(rep!=0)MaterialTheme.colorScheme.primary else Color.Gray)}}
  Slider(value=if(dur>0)pos.toFloat().coerceIn(0f,dur.toFloat())else 0f,onValueChange={pos=it.toLong()},onValueChangeFinished={p.seekTo(pos)},valueRange=0f..dur.coerceAtLeast(1).toFloat());Row{Text(fmt(pos),fontSize=11.sp,color=Color.Gray);Spacer(Modifier.weight(1f));Text(fmt(dur),fontSize=11.sp,color=Color.Gray)}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={prev()}){Icon(Icons.Default.SkipPrevious,null,Modifier.size(34.dp))};Spacer(Modifier.width(18.dp));IconButton(onClick={if(p.isPlaying)p.pause()else p.play()},Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary,CircleShape)){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color.Black,modifier=Modifier.size(32.dp))};Spacer(Modifier.width(18.dp));IconButton(onClick={next()}){Icon(Icons.Default.SkipNext,null,Modifier.size(34.dp))}}}
  }else Text("Choose a song to start listening",color=Color.Gray,modifier=Modifier.padding(bottom=12.dp))
 }
}
private fun fmt(ms:Long):String{val s=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(s/60,s%60)}
