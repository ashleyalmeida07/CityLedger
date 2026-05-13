// ── 5-Step Complaint Wizard ──
var step=1, maxStep=5, picked=null;
var QUESTIONS={
  'Pothole':[
    {q:'How big is the pothole?',opts:['Small (minor)','Medium (difficult to drive)','Large (dangerous)']},
    {q:'How long has it been there?',opts:['Just noticed','Few days','More than a week']},
    {q:'Has any accident happened?',opts:['Yes','No','Not sure']}
  ],
  'Street Lamp':[
    {q:'What is the issue?',opts:['Not working','Flickering','Broken/Damaged','Missing']},
    {q:'How many lamps affected?',opts:['1','2-3','More than 3']},
    {q:'Is the area dark at night?',opts:['Yes, completely','Partially','No, other lights nearby']}
  ],
  'Garbage':[
    {q:'Days since last collection?',opts:['1-2 days','3-5 days','More than 5 days']},
    {q:'Is it spreading on the road?',opts:['Yes','No']},
    {q:'Any health impact (smell/insects)?',opts:['Yes','No']}
  ],
  'Water Leakage':[
    {q:'Type of leakage?',opts:['Pipe burst','Tap leaking','Sewage overflow','Underground seepage']},
    {q:'How severe is the flow?',opts:['Dripping','Steady stream','Flooding']},
    {q:'Is it affecting traffic/walking?',opts:['Yes','No']}
  ],
  'Road Damage':[
    {q:'Type of damage?',opts:['Cracks','Sinking','Uneven surface','Missing cover/manhole']},
    {q:'Size of damaged area?',opts:['Small patch','Medium section','Large stretch']},
    {q:'Is it on a main road?',opts:['Yes','No']}
  ],
  'Tree Fall':[
    {q:'Current state?',opts:['Leaning dangerously','Partially fallen','Fully fallen','Branches hanging']},
    {q:'Is it blocking the road?',opts:['Yes, fully','Partially','No']},
    {q:'Any wires/structures affected?',opts:['Yes','No','Not sure']}
  ],
  'Noise':[
    {q:'Source of noise?',opts:['Construction','Loudspeaker','Factory/Industry','Vehicles','Other']},
    {q:'When does it occur?',opts:['Daytime only','Nighttime only','All day']},
    {q:'Duration?',opts:['Occasional','Daily','Continuous']}
  ],
  'Stray Animals':[
    {q:'Type of animal?',opts:['Dogs','Cattle','Pigs','Monkeys','Other']},
    {q:'Are they aggressive?',opts:['Yes','No','Sometimes']},
    {q:'Approximate count?',opts:['1-2','3-5','More than 5']}
  ],
  'Water Supply':[
    {q:'What is the issue?',opts:['No water','Low pressure','Dirty/colored water','Irregular timing']},
    {q:'How long has this been going on?',opts:['Today','Few days','More than a week']},
    {q:'How many households affected?',opts:['Just mine','Few neighbors','Whole area']}
  ],
  'Other':[
    {q:'Please classify the issue type:',opts:['Infrastructure','Sanitation','Safety/Security','Public Utility','Other']},
    {q:'How urgent is this issue?',opts:['Low priority','Needs attention soon','Urgent/Dangerous']},
    {q:'Are there multiple people affected?',opts:['Just me','Few people','Many people']}
  ]
};

function goStep(n){
  if(n<1||n>maxStep)return;
  if(n===2&&!picked){toast('warning','Pick a Category','Select an issue type first.');return;}
  if(n===4){buildPreviewTitle();}
  step=n;
  document.querySelectorAll('.wstep').forEach(function(s){s.style.display='none';});
  document.getElementById('step'+n).style.display='block';
  // progress
  for(var i=1;i<=maxStep;i++){
    var dot=document.getElementById('dot'+i);
    if(!dot)continue;
    dot.className=i<n?'pdot done':i===n?'pdot active':'pdot';
  }
}

function pickCat(btn){
  picked=btn.dataset.cat;
  document.querySelectorAll('.catcard').forEach(function(c){c.classList.remove('sel');});
  btn.classList.add('sel');
  document.getElementById('hiddenCategory').value=picked;
  // Build guided questions
  var qs=QUESTIONS[picked]||[];
  var html='';
  qs.forEach(function(q,i){
    html+='<div class="gq"><div class="gqlabel">'+q.q+'</div><div class="gqopts">';
    q.opts.forEach(function(o){
      html+='<label class="gqopt"><input type="radio" name="gq'+i+'" value="'+o+'" required><span>'+o+'</span></label>';
    });
    html+='</div></div>';
  });
  document.getElementById('guidedArea').innerHTML=html;
  setTimeout(function(){goStep(2);},200);
}

function buildPreviewTitle(){
  if(!picked)return;
  var answers={};
  var qs=QUESTIONS[picked]||[];
  qs.forEach(function(q,i){
    var r=document.querySelector('input[name="gq'+i+'"]:checked');
    answers[q.q]=r?r.value:'Not answered';
  });
  var extra=document.getElementById('extraNote').value.trim();
  var loc=document.getElementById('location').value||'Not set';
  // Auto-generate title from answers
  var title=picked+' — '+loc.substring(0,50);
  document.getElementById('autoTitle') && (document.getElementById('autoTitle').textContent=title);
  document.getElementById('hiddenTitle').value=title;
  // Build description from answers
  var desc=picked+' issue reported.\n';
  for(var k in answers)desc+=k+': '+answers[k]+'\n';
  if(extra)desc+='Note: '+extra;
  document.getElementById('hiddenDescription').value=desc;
  document.getElementById('hiddenGuidedAnswers').value=JSON.stringify(answers);
  document.getElementById('hiddenExtraNote').value=extra;
  // Show preview
  var prevHtml='<div class="prev-cat">'+picked+'</div>';
  prevHtml+='<div class="prev-loc">📍 '+loc+'</div>';
  prevHtml+='<div class="prev-answers">';
  for(var k in answers)prevHtml+='<div class="prev-row"><span class="prev-q">'+k+'</span><span class="prev-a">'+answers[k]+'</span></div>';
  prevHtml+='</div>';
  if(extra)prevHtml+='<div class="prev-note">'+extra+'</div>';
  document.getElementById('previewContent').innerHTML=prevHtml;
}

function captureGPS(){
  var btn=document.getElementById('gpsBtn');
  btn.textContent='⏳ Detecting...';btn.disabled=true;
  if(!navigator.geolocation){btn.textContent='❌ Not supported';return;}
  navigator.geolocation.getCurrentPosition(function(p){
    var lat=p.coords.latitude,lon=p.coords.longitude;
    document.getElementById('latitude').value=lat;
    document.getElementById('longitude').value=lon;
    document.getElementById('location').value=lat.toFixed(6)+', '+lon.toFixed(6);
    btn.textContent='✅ GPS Captured';btn.style.borderColor='#16a34a';btn.style.color='#16a34a';
    // reverse geocode
    fetch('https://nominatim.openstreetmap.org/reverse?lat='+lat+'&lon='+lon+'&format=json')
    .then(function(r){return r.json();})
    .then(function(d){if(d&&d.display_name)document.getElementById('location').value=d.display_name;})
    .catch(function(){});
  },function(){btn.textContent='❌ Denied';btn.disabled=false;},{enableHighAccuracy:true,timeout:10000});
}

// File preview
function handleFiles(input){
  var preview=document.getElementById('filePreview');
  preview.innerHTML='';
  if(!input.files.length)return;
  for(var i=0;i<input.files.length;i++){
    var f=input.files[i];
    if(f.size>10*1024*1024){toast('error','Too Large',f.name+' exceeds 10MB');input.value='';return;}
    if(f.type.startsWith('image/')){
      var r=new FileReader();
      r.onload=function(e){var img=document.createElement('img');img.src=e.target.result;preview.appendChild(img);};
      r.readAsDataURL(f);
    }
  }
}

// Toast
function toast(type,title,msg){
  var c=document.getElementById('toast-container');if(!c)return;
  var t=document.createElement('div');t.className='toast toast-'+type;
  t.innerHTML='<div class="toast-body"><strong>'+title+'</strong><br><small>'+msg+'</small></div><button onclick="this.parentElement.remove()" style="background:none;border:none;cursor:pointer">✕</button>';
  c.appendChild(t);
  setTimeout(function(){if(t.parentElement)t.remove();},4000);
}

// Submit
function submitWizard(){
  var lat=document.getElementById('latitude').value;
  if(!lat){toast('warning','Location Required','Please capture GPS or enter location.');return;}
  document.getElementById('submitBtn').disabled=true;
  document.getElementById('submitBtn').textContent='⏳ AI Processing...';
  document.getElementById('wizardForm').submit();
}
