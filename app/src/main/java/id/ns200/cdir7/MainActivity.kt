package id.ns200.cdir7

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity(), BleCdiClient.Listener {
    // Technical Dashboard / Data Grid tokens. System monospace keeps telemetry digits stable.
    private val canvas=Color.rgb(10,11,13); private val surface1=Color.rgb(17,18,20)
    private val surface1Alt=Color.rgb(21,22,25); private val surface2=Color.rgb(26,27,30)
    private val border=Color.rgb(34,34,34); private val borderStrong=Color.rgb(51,51,51)
    private val primary=Color.rgb(242,125,38); private val success=Color.rgb(0,255,0)
    private val telemetry=Color.rgb(0,229,255); private val hazard=Color.rgb(255,68,68)
    private val textPrimary=Color.rgb(240,242,244); private val muted=Color.rgb(154,160,166)
    private val pickSoundRequest=88
    private lateinit var client:BleCdiClient; private lateinit var sound:EngineSound
    private lateinit var status:TextView; private lateinit var gauge:RpmGaugeView
    private lateinit var metrics:Array<TextView>; private lateinit var setupInfo:TextView
    private lateinit var pickupInfo:TextView; private lateinit var outputInfo:TextView
    private lateinit var diagnostics:TextView; private lateinit var offsetField:EditText
    private lateinit var stationary:LinearLayout; private lateinit var soundToggle:Switch
    private lateinit var soundSpinner:Spinner; private lateinit var soundBase:EditText
    private val pages=mutableListOf<View>(); private val navButtons=mutableListOf<Button>()
    private var activePage=0; private var log="Belum ada telemetry"

    override fun onCreate(s:Bundle?){super.onCreate(s);client=BleCdiClient(this,this);sound=EngineSound(this);setContentView(ui());restoreSound();requestBtPermissions()}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun text(v:String,size:Float=14f,color:Int=textPrimary)=TextView(this).apply{text=v;textSize=size;setTextColor(color);typeface=Typeface.MONOSPACE;setPadding(dp(9),dp(6),dp(9),dp(6))}
    private fun button(v:String,run:()->Unit)=Button(this).apply{text=v;isAllCaps=true;typeface=Typeface.create(Typeface.MONOSPACE,Typeface.BOLD);textSize=12f;setTextColor(Color.BLACK);backgroundTintList=android.content.res.ColorStateList.valueOf(primary);setOnClickListener{run()}}
    private fun danger(v:String,run:()->Unit)=button(v,run).apply{setTextColor(Color.WHITE);backgroundTintList=android.content.res.ColorStateList.valueOf(hazard)}
    private fun field(h:String,v:String)=EditText(this).apply{hint=h;setText(v);typeface=Typeface.MONOSPACE;setTextColor(textPrimary);setHintTextColor(muted);inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED;backgroundTintList=android.content.res.ColorStateList.valueOf(telemetry)}
    private fun spinner(items:List<String>)=Spinner(this).apply{
        adapter=object:ArrayAdapter<String>(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,items){
            private fun tune(view:View):View=view.apply{if(this is TextView){typeface=Typeface.MONOSPACE;setTextColor(textPrimary);setBackgroundColor(surface2);setPadding(dp(10),dp(8),dp(10),dp(8))}}
            override fun getView(position:Int,convertView:View?,parent:ViewGroup)=tune(super.getView(position,convertView,parent))
            override fun getDropDownView(position:Int,convertView:View?,parent:ViewGroup)=tune(super.getDropDownView(position,convertView,parent))
        }
    }
    private fun row(vararg views:View)=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;views.forEach{addView(it,LinearLayout.LayoutParams(0,-2,1f))}}
    private fun card(title:String,desc:String,child:View?=null,warn:Boolean=false)=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL;setPadding(dp(11),dp(9),dp(11),dp(11));background=GradientDrawable().apply{cornerRadius=dp(3).toFloat();setColor(surface2);setStroke(dp(1),if(warn)hazard else borderStrong)}
        addView(text(title.uppercase(),14f,if(warn)hazard else primary).apply{setTypeface(typeface,Typeface.BOLD)});addView(text(desc,12f,textPrimary));child?.let{addView(it)}
    }.also{it.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(6),0,dp(6))}}
    private fun page(fill:LinearLayout.()->Unit)=ScrollView(this).apply{addView(LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(30));fill()})}

    private fun ui():View{
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(canvas)}
        val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;addView(text("NS200 // CDI R7",20f,primary).apply{setTypeface(typeface,Typeface.BOLD)});addView(text("STM32WB55  |  BLE TELEMETRY  |  ECU CONTROL",10f,muted))}
        val top=row(title,button("CONNECT"){if(hasBtPermissions())client.connect()else requestBtPermissions()})
        status=text("● OFFLINE  |  GATT --  |  IGNITION --",11f,hazard).apply{setBackgroundColor(surface1);setPadding(dp(12),dp(8),dp(12),dp(8))};root.addView(top);root.addView(status)
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};arrayOf("DASH","SETUP","TUNE","GUIDE").forEachIndexed{i,n->val b=button(n){show(i)};navButtons+=b;nav.addView(b,LinearLayout.LayoutParams(dp(112),dp(46)).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})}
        root.addView(HorizontalScrollView(this).apply{isHorizontalScrollBarEnabled=false;addView(nav)})
        val frame=FrameLayout(this);pages+=dashboard();pages+=quick();pages+=tuning();pages+=guide();pages.forEach{frame.addView(it,FrameLayout.LayoutParams(-1,-1))};root.addView(frame,LinearLayout.LayoutParams(-1,0,1f));show(0);return root
    }
    private fun show(i:Int){activePage=i;pages.forEachIndexed{n,v->v.visibility=if(n==i)View.VISIBLE else View.GONE};navButtons.forEachIndexed{n,b->b.setTextColor(if(n==i)Color.BLACK else textPrimary);b.backgroundTintList=android.content.res.ColorStateList.valueOf(if(n==i)primary else surface1Alt)}}

    private fun dashboard()=page{
        gauge=RpmGaugeView(this@MainActivity);addView(gauge,LinearLayout.LayoutParams(-1,dp(330)))
        val grid=GridLayout(this@MainActivity).apply{columnCount=2};val names=arrayOf("TPS","ADVANCE","BATTERY","HV CENTER","HV SIDE","TEMP","MAP","LIMITER")
        metrics=Array(8){i->
            text(names[i]+"\n--",14f,telemetry).apply{
                gravity=Gravity.CENTER
                setTypeface(typeface,Typeface.BOLD)
                background=GradientDrawable().apply{
                    cornerRadius=dp(2).toFloat();setColor(surface2);setStroke(dp(1),border)
                }
            }
        }
        metrics.forEach{
            grid.addView(it,GridLayout.LayoutParams().apply{
                width=resources.displayMetrics.widthPixels/2-dp(20)
                height=dp(68);setMargins(dp(3),dp(3),dp(3),dp(3))
            })
        }
        addView(grid)
        setupInfo=text("Setup: menunggu koneksi",14f,primary);outputInfo=text("Output: --",12f,muted);addView(card("STATUS CDI","Status utama selalu terlihat.",LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(setupInfo);addView(outputInfo)}))
        val box=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL};soundSpinner=spinner(EngineSound.Preset.entries.map{it.label}).apply{onItemSelectedListener=object:AdapterView.OnItemSelectedListener{override fun onItemSelected(p:AdapterView<*>?,v:View?,i:Int,id:Long){sound.select(EngineSound.Preset.entries[i])};override fun onNothingSelected(p:AdapterView<*>?)=Unit}}
        soundToggle=Switch(this@MainActivity).apply{text="SOUND OFF";typeface=Typeface.MONOSPACE;setTextColor(textPrimary);buttonTintList=android.content.res.ColorStateList.valueOf(primary);setOnCheckedChangeListener{_,on->sound.enabled=on;text=if(on)"SOUND ON" else "SOUND OFF"}};box.addView(row(soundSpinner,soundToggle));soundBase=field("RPM rekaman","2000");box.addView(row(soundBase,button("UPLOAD AUDIO"){pickSound()}));box.addView(text("Mengikuti RPM telemetry; output ke HP/interkom/MH-M18 + amplifier.",12f,muted));addView(card("VIRTUAL ENGINE SOUND","Preset 1/2/3/4 silinder atau file loop sendiri.",box))
        diagnostics=text(log,12f,muted);addView(card("DIAGNOSTIK","BLE, CRC dan fault.",diagnostics))
    }

    private fun quick()=page{
        addView(text("QUICK SETUP // SATU FIRMWARE",18f,primary).apply{setTypeface(typeface,Typeface.BOLD)})
        addView(card("Tidak perlu flash ulang","Firmware yang sama menyimpan tahap BARU → PULSER OK → TDC → FIRST START → READY.",warn=true))
        addView(card("1. Catu dan harness","SW_ARM OFF, JP_HV lepas. J1.5→proteksi/buck 5 V; J1.11→GND_STAR. Pin VB/5V WeAct tidak boleh menerima 12 V.",button("Lihat wiring"){show(3)}))
        val p=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;pickupInfo=text("Kualitas pulser --",14f,primary);addView(pickupInfo);val edge=spinner(listOf("FALLING","RISING"));addView(row(edge,button("Simpan edge"){confirm("Ubah edge","Mengubah edge membatalkan PULSER/TDC lama dan menonaktifkan koil sampai kalibrasi ulang."){send("SETUP,EDGE,"+edge.selectedItem)}}));val ppr=field("Pulse/revolution","1");addView(row(ppr,button("Simpan PPR"){confirm("Ubah PPR","Mengubah PPR membatalkan TDC dan mewajibkan kalibrasi TDC ulang."){send("SETUP,PPR,"+ppr.text)}}));val gate=field("Pulse gate SCR us","80");addView(row(gate,button("Simpan gate"){send("SETUP,GATE_US,"+gate.text)}));addView(button("Konfirmasi PULSER OK"){send("SETUP,PICKUP,CONFIRM")})}
        addView(card("2. Uji pulser","J1.10→39k→LM339 pin5; LM339 pin2→1k→PA0. JP_HV lepas. Default PPR=1 dan gate=80us. Starter 2-3 detik sampai kualitas ≥10, lepaskan lalu konfirmasi.",p))
        val t=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;offsetField=field("Trigger offset","60.00");addView(offsetField);addView(row(button("-10°"){offset(-10.0)},button("-1°"){offset(-1.0)},button("+1°"){offset(1.0)},button("+10°"){offset(10.0)}));addView(row(button("Mulai strobo"){send("SETUP,STROBE,ON")},button("Stop strobo"){send("SETUP,STROBE,OFF")}));addView(button("SAVE TDC setelah sejajar"){send("SETUP,SAVE_TDC")});addView(text("Tanpa LED hanya boleh memasukkan offset yang sudah terukur, bukan tebakan.",12f,hazard));addView(danger("Simpan offset terverifikasi TANPA LED"){val x=offsetCdeg();if(x!=null)confirm("Offset manual","Pastikan angka berasal dari pengukuran sah."){send("SETUP,MANUAL_TDC,$x,CONFIRM")}})}
        addView(card("3. Kalibrasi TDC","PB9→100Ω→Gate MOSFET; Gate→10k→GND; Source→GND; Drain→LED(-); LED(+)→5 V. JP_HV lepas, SW_ARM OFF. Geser sampai tanda T sejajar garis crankcase.",t))
        addView(card("4. TPS","Mesin mati/JP_HV lepas. J1.2/J1.4→selector; sinyal→PA3, reference monitor→PA5.",row(button("Gas tertutup"){send("SETUP,TPS,CLOSED")},button("Gas penuh"){send("SETUP,TPS,OPEN")})))
        val f=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(button("Siapkan FIRST START"){confirm("First Start","Setelah ACK pasang JP_HV, SW_ARM ON, starter. Otomatis 220 V, CENTER saja, ≤10°, limiter 3.000."){send("SETUP,FIRST_START")}});addView(text("Sesudah hidup ≥3 detik: SW_ARM OFF untuk mematikan pengapian tanpa mematikan kontak/STM32; lepas JP_HV dan tunggu HV <30 V.",12f,hazard));addView(button("READY - CENTER saja"){send("SETUP,READY,CENTER")});val side=field("Offset SIDE terukur","0.00");addView(side);addView(danger("READY - 3 busi"){val x=(side.text.toString().toDoubleOrNull()?.times(100))?.toInt();if(x!=null)confirm("Aktifkan SIDE","Hanya dengan offset SIDE terukur; rentang ±30°."){send("SETUP,READY,THREE,$x")}})}
        addView(card("5. First Start lalu READY","PA1→driver SCR1→C_CENTER→J1.12. PA2→driver SCR2→C_SIDE→J1.6. Kabel koil tidak pernah langsung ke STM32.",f,true))
        addView(danger("RESET QUICK SETUP"){confirm("Reset","Menghapus TDC/TPS/READY dan menonaktifkan koil."){send("SETUP,RESET,CONFIRM")}})
    }

    private fun tuning()=page{
        addView(card("Batas R7","NORMAL 8×4/285 V/≤36°. PRO 16×8/290 V/JP_PRO. 345 V diblokir: manual servis menyebut puncak primer 100-300 V.",warn=true))
        val live=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;val ti=field("TPS index","0");val ri=field("RPM index","0");val av=field("Advance°","5.0");addView(row(ti,ri,av));addView(button("APPLY CELL"){val d=av.text.toString().toDoubleOrNull();if(d!=null)send("LIVE,"+ti.text+","+ri.text+","+(d*100).toInt())});addView(text("Saat hidup perubahan per langkah maksimal ±2°. Kurva dasar konservatif 5°/1.500 sampai 36°/10.000; bukan kurva OEM.",12f,muted))};addView(card("CURVE / LIVE TUNING","Edit sel RPM×TPS.",live))
        stationary=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;val kind=spinner(listOf("SOFT","HARD"));val rpm=field("RPM limit","9500");val band=field("Soft band","400");addView(row(kind,rpm,band));addView(button("SET LIMITER"){send("LIMIT,"+kind.selectedItem+","+rpm.text+","+band.text)});val load=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL};val save=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.HORIZONTAL};arrayOf("ECO","STREET","RAIN","PRO").forEachIndexed{i,n->load.addView(button("Load $n"){send("LOAD,$i")},LinearLayout.LayoutParams(0,-2,1f));save.addView(button("Save $n"){send("SAVE,$i")},LinearLayout.LayoutParams(0,-2,1f))};addView(load);addView(save);addView(text("Hanya RPM=0, JP_HV lepas, kedua HV <30 V. PRO butuh JP_PRO/PB4.",12f,muted))};addView(card("LIMITER & 4 MEMORY","ECO/STREET/RAIN/PRO, CRC flash.",stationary))
        addView(card("KIPAS","PB5→4.7k→basis BC547; emitter GND; collector→J1.7. AUTO fail-safe ON sampai kurva suhu divalidasi.",row(button("OFF"){send("SETUP,FAN,OFF")},button("ON"){send("SETUP,FAN,ON")},button("AUTO"){send("SETUP,FAN,AUTO")})))
    }

    private fun guide()=page{
        addView(card("Bluetooth dan PIN","Tekan Hubungkan di aplikasi. Saat pairing pertama muncul, masukkan PIN 123456. Jangan melakukan Pair manual sebelum aplikasi. Jika pernah memakai build lama atau GATT 8 terus berulang: Pengaturan Bluetooth → Lupakan NS200-CDI-R7, matikan/nyalakan Bluetooth, lalu Hubungkan lagi. Aplikasi mencoba ulang timeout dua kali."))
        addView(card("Orientasi soket","Muka soket HARNESS, latch di atas: 1-6 atas kiri→kanan; 7-12 bawah kiri→kanan. Gunakan pigtail."))
        addView(card("J1 kabel demi kabel","""J1.1 NC | J1.2 hijau/putih TPS A | J1.3 hitam/putih TEMP
J1.4 abu TPS B | J1.5 +12V kontak | J1.6 koil SIDE
J1.7 biru/kuning fan | J1.8 NC | J1.9 NC
J1.10 putih/merah pulser | J1.11 hitam/kuning GND | J1.12 koil CENTER"""))
        addView(card("WeAct pins","""PA0 pulser | PA1 CENTER | PA2 SIDE | PA3 TPS | PA4 TEMP | PA5 TPS REF
PA6 HV CENTER | PA7 HV SIDE | PA9/PB8 charger | PA10 fault
PB0 aki | PB2 JP_HV | PB3 SW_ARM | PB4 JP_PRO | PB5 fan | PB9 strobo
5V/GND dari buck 5.00 V. Pin VB bukan input aki."""))
        addView(card("Charger HV","""PA9→1k→TC4427 pin2; PB8→1k→pin4; pin3→GND_POWER; pin6→VIN_HV
pin7/pin5→10Ω→Gate IRF3205 Q1/Q2. VIN_HV→center tap lilitan 5V trafo ATX; ujung→drain Q1/Q2. Sekunder→4×UF4007 bridge→dua bank HV. Trafo dipakai utuh, tidak dililit.""",warn=true))
        addView(card("Koil/HV","""HV_CENTER→C 1uF/630V MKP pulse→J1.12; BT151 Anode→HV, Cathode→GND, Gate dari PA1 driver.
HV_SIDE identik→J1.6, Gate dari PA2. 4×470k bleeder tiap C. Clearance HV ≥6mm; PCB power terpisah.""",warn=true))
        addView(card("Audio opsional","""Aki+→fuse 5A→SB560→IRF4905→XL4015 11.5V→PAM8610. LM2596 5V→MH-M18. L/R MH-M18→film 1uF→LIN/RIN. Speaker 8Ω ≥15W ke L+/L- dan R+/R-. Output BTL: minus speaker tidak ke ground."""))
        addView(card("Pemakaian","Flash NS200_CDI_R7.hex sekali → ikuti QUICK SETUP → READY tersimpan. Kontak berikutnya langsung memakai kalibrasi/map. BLE boleh putus; timing tetap berjalan lokal."))
        addView(card("STOP","Kickback/backfire, timing meloncat, HV ≥300V, beda bank >50V, fault/reset/panas/detonasi. Prototipe belum tervalidasi otomotif/EMC.",warn=true))
    }

    private fun send(s:String){if(!client.send(s))Toast.makeText(this,"BLE belum siap",Toast.LENGTH_SHORT).show()}
    private fun offsetCdeg():Int?{val v=offsetField.text.toString().toDoubleOrNull()?:return null;return if(v in 0.0..359.99)(v*100).toInt() else null}
    private fun offset(d:Double){val v=((offsetField.text.toString().toDoubleOrNull()?:60.0)+d).coerceIn(0.0,359.99);offsetField.setText("%.2f".format(v));send("SETUP,OFFSET,"+(v*100).toInt())}
    private fun confirm(t:String,m:String,run:()->Unit){AlertDialog.Builder(this).setTitle(t).setMessage(m).setNegativeButton("Batal",null).setPositiveButton("Lanjut"){_,_->run()}.show()}
    private fun pickSound(){sound.setCustomBaseRpm(soundBase.text.toString().toIntOrNull()?:2000);startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*";flags=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION},pickSoundRequest)}
    private fun restoreSound(){val p=getSharedPreferences("manual_sound",MODE_PRIVATE);val u=p.getString("uri",null)?:return;val r=p.getInt("base_rpm",2000);soundBase.setText(r.toString());sound.setCustom(Uri.parse(u),r);soundSpinner.setSelection(EngineSound.Preset.CUSTOM.ordinal)}
    private fun requestBtPermissions(){requestPermissions(if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT)else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),77)}
    private fun hasBtPermissions()=if(Build.VERSION.SDK_INT>=31)checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED else checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    @Deprecated("Android 10") override fun onActivityResult(req:Int,res:Int,data:Intent?){super.onActivityResult(req,res,data);if(req!=pickSoundRequest||res!=RESULT_OK)return;val u=data?.data?:return;try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:SecurityException){};val r=soundBase.text.toString().toIntOrNull()?.coerceIn(600,8000)?:2000;sound.setCustom(u,r);soundSpinner.setSelection(EngineSound.Preset.CUSTOM.ordinal);getSharedPreferences("manual_sound",MODE_PRIVATE).edit().putString("uri",u.toString()).putInt("base_rpm",r).apply()}
    override fun onState(v:String,c:Boolean)=runOnUiThread{status.text=(if(c)"● ONLINE  |  " else "● OFFLINE  |  ")+v.uppercase();status.setTextColor(if(c)success else hazard);if(c)Handler(Looper.getMainLooper()).postDelayed({send("GET,SETUP")},900)else{sound.enabled=false;soundToggle.isChecked=false}}
    override fun onTelemetry(v:Telemetry)=runOnUiThread{gauge.rpm=v.rpm;sound.update(v);metrics[0].text="TPS\n${v.tps/10.0}%";metrics[1].text="ADVANCE\n${v.advanceCdeg/100.0}°";metrics[2].text="BATTERY\n${v.batteryCv/100.0}V";metrics[3].text="HV CENTER\n${v.hvCenter}V";metrics[4].text="HV SIDE\n${v.hvSide}V";metrics[5].text="TEMP\n"+(if(v.tempCdeg==Short.MIN_VALUE.toInt())"--" else "${v.tempCdeg/100.0}°C");metrics[6].text="MAP\n"+arrayOf("ECO","STREET","RAIN","PRO").getOrElse(v.slot){"?"};metrics[7].text="LIMITER\n"+arrayOf("FIRE","SOFT","HARD").getOrElse(v.limiter){"?"};val st=arrayOf("BARU","PULSER OK","TDC","FIRST START","READY").getOrElse(v.setupStage){"?"};setupInfo.text="Setup $st • Trigger ${v.triggerCdeg/100.0}° • First ${v.firstStartSeconds}s";setupInfo.setTextColor(if(v.ready)success else primary);pickupInfo.text="Kualitas pulser ${v.pickupQuality}/100";pickupInfo.setTextColor(if(v.pickupQuality>=10)success else primary);outputInfo.text="CENTER ${v.centerEnabled} • SIDE ${v.sideEnabled} • FAN ${v.fanEnabled} • STROBE ${v.strobeEnabled}";if(!offsetField.hasFocus())offsetField.setText("%.2f".format(v.triggerCdeg/100.0));val safe=v.rpm==0&&!v.hvEnabled&&v.hvCenter<30&&v.hvSide<30;enable(stationary,safe);stationary.alpha=if(safe)1f else .42f;log="SEQ ${v.sequence} CRC OK ARM ${v.armed} HV ${v.hvEnabled} READY ${v.ready} FAULT 0x%04X".format(v.faults);diagnostics.text=log}
    private fun enable(v:View,on:Boolean){v.isEnabled=on;if(v is ViewGroup)for(i in 0 until v.childCount)enable(v.getChildAt(i),on)}
    override fun onResponse(v:String)=runOnUiThread{log=v+"\n"+log.take(500);diagnostics.text=log;if(v.contains("ACK"))Handler(Looper.getMainLooper()).postDelayed({send("GET,SETUP")},250)}
    override fun onDestroy(){client.disconnect();sound.release();super.onDestroy()}
}
