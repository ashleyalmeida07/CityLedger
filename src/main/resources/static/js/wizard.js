// ── 6-Step Enhanced Complaint Wizard ──
var step=1, maxStep=6, picked=null;
var QUESTIONS={
  'Pothole':[
    {q:'How big is the pothole?',opts:['Small (< 6 inches, minor bump)','Medium (6-12 inches, difficult to drive)','Large (> 12 inches, dangerous)','Multiple potholes in area']},
    {q:'How long has it been there?',opts:['Just noticed today','2-7 days','1-2 weeks','More than 2 weeks']},
    {q:'Has any accident or damage occurred?',opts:['Yes, vehicle damage reported','Yes, injury reported','Near misses/close calls','No incidents yet']},
    {q:'What is the traffic volume in this area?',opts:['Heavy (main road)','Moderate (residential street)','Light (side street)','Very light (rarely used)']}
  ],
  'Street Lamp':[
    {q:'What is the specific issue?',opts:['Completely not working','Flickering/intermittent','Broken/physically damaged','Missing/stolen','Dim/insufficient light']},
    {q:'How many lamps are affected?',opts:['Single lamp','2-3 lamps','4-10 lamps','More than 10 lamps','Entire street']},
    {q:'How dark is the area at night?',opts:['Completely dark (safety hazard)','Very dark (difficult to see)','Partially lit (some visibility)','Adequately lit by other sources']},
    {q:'When did you first notice this issue?',opts:['Today','This week','This month','Longer than a month']}
  ],
  'Garbage':[
    {q:'How many days since last collection?',opts:['1-2 days overdue','3-5 days overdue','6-10 days overdue','More than 10 days','Never collected']},
    {q:'Is garbage spreading beyond the designated area?',opts:['Yes, covering road/footpath','Yes, scattered by animals','Slightly overflowing','Contained in designated area']},
    {q:'What health/safety impacts are present?',opts:['Strong odor + insects/rodents','Moderate odor, some insects','Mild odor only','No noticeable impact yet']},
    {q:'Approximate volume of accumulated garbage?',opts:['Small pile (few bags)','Medium pile (cart-load)','Large pile (truck-load)','Massive accumulation']}
  ],
  'Water Leakage':[
    {q:'What type of water leakage is it?',opts:['Major pipe burst (gushing water)','Broken water main','Leaking tap/valve','Sewage overflow','Underground seepage/wet patch']},
    {q:'How severe is the water flow?',opts:['Flooding (standing water)','Heavy flow (steady stream)','Moderate leak (continuous drip)','Minor seepage']},
    {q:'Is it affecting traffic or pedestrians?',opts:['Yes, road flooded/impassable','Yes, footpath affected','Partially affecting movement','Not affecting movement yet']},
    {q:'How long has this been leaking?',opts:['Just started (< 1 hour)','Few hours','1-2 days','More than 2 days']}
  ],
  'Road Damage':[
    {q:'What type of road damage?',opts:['Deep cracks/fissures','Road sinking/depression','Uneven surface/bumps','Missing manhole cover','Broken/damaged cover','Erosion/washout']},
    {q:'Size of the damaged area?',opts:['Small patch (< 2 meters)','Medium section (2-5 meters)','Large stretch (5-20 meters)','Very large area (> 20 meters)']},
    {q:'What type of road is this?',opts:['Major highway/arterial road','Main city road','Residential street','Internal/colony road']},
    {q:'Is there immediate danger?',opts:['Yes, high risk of accidents','Moderate risk, needs caution','Low risk but deteriorating','No immediate danger']}
  ],
  'Tree Fall':[
    {q:'Current state of the tree?',opts:['Fully fallen (on ground)','Partially fallen (leaning)','Dangerously leaning (about to fall)','Large branches hanging/broken','Tree uprooted']},
    {q:'Is it blocking any road or path?',opts:['Yes, completely blocking road','Yes, partially blocking road','Blocking footpath only','Not blocking, but dangerous']},
    {q:'Are any structures or utilities affected?',opts:['Yes, power lines damaged','Yes, building/property damaged','Yes, vehicles trapped/damaged','No structures affected']},
    {q:'Approximate size of the tree?',opts:['Large tree (> 20 feet)','Medium tree (10-20 feet)','Small tree (< 10 feet)','Large branch only']}
  ],
  'Noise':[
    {q:'What is the source of noise?',opts:['Construction/demolition work','Loudspeaker/PA system','Factory/industrial machinery','Vehicles/traffic','Generator/equipment','Other source']},
    {q:'When does the noise occur?',opts:['Late night (10 PM - 6 AM)','Early morning (6 AM - 8 AM)','Daytime only','All day and night']},
    {q:'How frequent is the noise?',opts:['Continuous (non-stop)','Daily (regular schedule)','Several times per week','Occasional/intermittent']},
    {q:'Noise intensity level?',opts:['Extremely loud (unbearable)','Very loud (disturbing)','Moderately loud (annoying)','Noticeable but tolerable']}
  ],
  'Stray Animals':[
    {q:'What type of animals?',opts:['Dogs (stray/street)','Cattle (cows/buffaloes)','Pigs','Monkeys','Other animals']},
    {q:'Are they showing aggressive behavior?',opts:['Yes, have attacked people','Yes, threatening/chasing','Sometimes aggressive','Generally peaceful']},
    {q:'Approximate number of animals?',opts:['1-2 animals','3-5 animals','6-10 animals','More than 10 animals','Large pack/herd']},
    {q:'What problems are they causing?',opts:['Safety threat to people','Traffic obstruction','Property damage','Garbage scattering','Multiple issues']}
  ],
  'Water Supply':[
    {q:'What is the specific water supply issue?',opts:['No water supply at all','Very low pressure','Dirty/muddy water','Foul smell/taste','Irregular/unpredictable timing']},
    {q:'How long has this problem persisted?',opts:['Started today','2-3 days','4-7 days','1-2 weeks','More than 2 weeks']},
    {q:'How many households/people affected?',opts:['Just my household','2-5 households','6-20 households','Entire street/area','Multiple streets']},
    {q:'Is there any alternative water source?',opts:['No alternative available','Buying water (expensive)','Using tanker water','Using well/borewell','Other source available']}
  ],
  'Other':[
    {q:'Please classify the general issue type:',opts:['Infrastructure (roads, buildings)','Sanitation/hygiene','Safety/security concern','Public utility problem','Environmental issue','Other civic issue']},
    {q:'How urgent is this issue?',opts:['Emergency (immediate danger)','High priority (needs quick action)','Medium priority (needs attention)','Low priority (can wait)']},
    {q:'How many people are affected?',opts:['Just me/my family','Few neighbors (2-5)','Many people (10-50)','Large community (50+)','Entire area']},
    {q:'Have you reported this before?',opts:['Yes, multiple times (no action)','Yes, once (no response)','No, first time reporting','Not sure']}
  ]
};

function goStep(n){
  if(n<1||n>maxStep)return;
  if(n===2&&!picked){toast('warning','Pick a Category','Select an issue type first.');return;}
  if(n===5){buildPreviewTitle();}
  step=n;
  document.querySelectorAll('.wcard').forEach(function(s){s.style.display='none';});
  var stepEl = document.getElementById('step'+n);
  if(stepEl) stepEl.style.display='block';
  // progress
  for(var i=1;i<=maxStep;i++){
    var dot=document.getElementById('dot'+i);
    if(!dot)continue;
    dot.className=i<n?'pdot done':i===n?'pdot active':'pdot';
    var line=document.getElementById('line'+i);
    if(line) line.className=i<n?'pline done':'pline';
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
    html+='<div class="gq"><div class="gqlabel"><span class="q-num">'+(i+1)+'.</span> '+q.q+'</div><div class="gqopts">';
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
  if(extra)desc+='Additional Details: '+extra;
  document.getElementById('hiddenDescription').value=desc;
  document.getElementById('hiddenGuidedAnswers').value=JSON.stringify(answers);
  document.getElementById('hiddenExtraNote').value=extra;
  // Show preview
  var prevHtml='<div class="prev-cat">'+picked+'</div>';
  prevHtml+='<div class="prev-loc" style="display:flex;align-items:center;gap:6px;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg> '+loc+'</div>';
  prevHtml+='<div class="prev-answers">';
  var qNum = 1;
  for(var k in answers){
    prevHtml+='<div class="prev-row"><span class="prev-q"><strong>Q'+qNum+':</strong> '+k+'</span><span class="prev-a">'+answers[k]+'</span></div>';
    qNum++;
  }
  prevHtml+='</div>';
  if(extra)prevHtml+='<div class="prev-note"><strong>Additional Details:</strong><br>'+extra+'</div>';
  document.getElementById('previewContent').innerHTML=prevHtml;
}

function captureGPS(){
  var btn=document.getElementById('gpsBtn');
  btn.textContent='📍 Detecting...';btn.disabled=true;
  if(!navigator.geolocation){btn.textContent='❌ Not supported';return;}
  navigator.geolocation.getCurrentPosition(function(p){
    var lat=p.coords.latitude,lon=p.coords.longitude;
    document.getElementById('latitude').value=lat;
    document.getElementById('longitude').value=lon;
    document.getElementById('location').value=lat.toFixed(6)+', '+lon.toFixed(6);
    document.getElementById('locationDisplay').value=lat.toFixed(6)+', '+lon.toFixed(6);
    btn.textContent='✓ GPS Captured';btn.style.borderColor='#16a34a';btn.style.color='#16a34a';
    // reverse geocode
    fetch('https://nominatim.openstreetmap.org/reverse?lat='+lat+'&lon='+lon+'&format=json')
    .then(function(r){return r.json();})
    .then(function(d){if(d&&d.display_name){document.getElementById('locationDisplay').value=d.display_name;document.getElementById('location').value=d.display_name;}})
    .catch(function(){});
  },function(){btn.textContent='❌ Denied';btn.disabled=false;},{enableHighAccuracy:true,timeout:10000});
}

// File preview
function handleFiles(input){
  var fprev = document.getElementById('filePreview');
  fprev.innerHTML = '';
  if(!input.files.length)return;
  for(var i=0;i<input.files.length;i++){
    var f=input.files[i];
    if(f.size>10*1024*1024){toast('error','Too Large',f.name+' exceeds 10MB');input.value='';fprev.innerHTML='';return;}
    if(f.type.startsWith('image/')){
      var img = document.createElement('img');
      img.src = URL.createObjectURL(f);
      fprev.appendChild(img);
    } else if(f.type.startsWith('video/')){
      var vid = document.createElement('video');
      vid.src = URL.createObjectURL(f);
      vid.style.width = '80px';
      vid.style.height = '80px';
      vid.style.objectFit = 'cover';
      vid.style.borderRadius = '8px';
      vid.style.border = '2px solid #e2e8f0';
      fprev.appendChild(vid);
    }
  }
}

// Toast — uses id="tc" as container
function toast(type,title,msg){
  var c=document.getElementById('tc');if(!c)return;
  var t=document.createElement('div');t.className='toast toast-'+type;
  t.innerHTML='<div class="toast-body"><strong>'+title+'</strong><br><small>'+msg+'</small></div><button onclick="this.parentElement.remove()" style="background:none;border:none;cursor:pointer;margin-left:auto;font-size:1.2rem;color:#666">×</button>';
  c.appendChild(t);
  setTimeout(function(){if(t.parentElement)t.remove();},5000);
}

// Submit
function submitWizard(){
  var loc=document.getElementById('location').value;
  if(!loc){toast('warning','Location Required','Please capture GPS or enter location.');return;}
  var btn=document.getElementById('submitBtn');
  btn.disabled=true;
  btn.textContent='Processing...';

  var progOv = document.createElement('div');
  progOv.className='sov';
  progOv.style.display='flex';

  progOv.innerHTML = 
    '<div class="scard" style="text-align:center;max-width:420px;padding:40px;width:90%">' +
    '<div style="width:64px;height:64px;margin:0 auto 20px;background:linear-gradient(135deg,#667eea,#764ba2);border-radius:50%;display:flex;align-items:center;justify-content:center">' +
    '<svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2"><path d="M22 11.08V12a10 10 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>' +
    '</div>' +
    '<h3 style="font-family:\'Urbanist\',sans-serif;font-size:1.6rem;font-weight:900;color:#0f3460;margin-bottom:12px">Submitting Your Report</h3>' +
    '<p id="progText" style="color:#64748b;font-size:1rem;margin-bottom:28px;font-weight:600">Uploading data & media...</p>' +
    '<div style="width:100%;height:10px;background:#e2e8f0;border-radius:10px;overflow:hidden;margin-bottom:16px">' +
    '<div id="progBar" style="height:100%;width:15%;background:linear-gradient(90deg,#667eea,#764ba2);transition:width 0.6s ease-in-out;border-radius:10px"></div>' +
    '</div>' +
    '<p style="font-size:0.85rem;color:#94a3b8">Please wait, this may take a few moments...</p>' +
    '</div>';
  document.body.appendChild(progOv);

  var fd = new FormData(document.getElementById('wizardForm'));

  setTimeout(function(){
      if(document.getElementById('progText')) {
          document.getElementById('progText').textContent = 'AI analyzing and categorizing...';
          document.getElementById('progBar').style.width = '40%';
      }
  }, 1000);

  setTimeout(function(){
      if(document.getElementById('progText')) {
          document.getElementById('progText').textContent = 'Checking for duplicates...';
          document.getElementById('progBar').style.width = '60%';
      }
  }, 2500);

  setTimeout(function(){
      if(document.getElementById('progText')) {
          document.getElementById('progText').textContent = 'Recording on blockchain...';
          document.getElementById('progBar').style.width = '85%';
      }
  }, 4000);

  fetch('/citizen/report', {
    method: 'POST',
    body: fd,
    redirect: 'manual'
  }).then(function(res){
     if(res.type === 'opaqueredirect' || (res.ok && res.url && res.url.includes('success=true'))) {
        if(document.getElementById('progText')) {
            document.getElementById('progText').textContent = 'Report submitted successfully!';
            document.getElementById('progBar').style.width = '100%';
            document.getElementById('progBar').style.background = 'linear-gradient(90deg,#10b981,#059669)';
        }
        
        setTimeout(function() {
            progOv.remove();
            window.location.href = res.url || '/citizen/report?success=true';
        }, 1000);
     } else if(res.redirected) {
        setTimeout(function() {
            progOv.remove();
            window.location.href = res.url;
        }, 1000);
     } else {
        progOv.remove();
        toast('error','Submission Failed','Something went wrong. Please try again.');
        btn.disabled=false;
        btn.textContent='Submit Report';
     }
  }).catch(function(e){
     progOv.remove();
     toast('error','Network Error','Unable to submit report: ' + e.toString());
     btn.disabled=false;
     btn.textContent='Submit Report';
  });
}
