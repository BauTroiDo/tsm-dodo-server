import './style.css';
import {
  SelectAPK, SelectDirectory, SelectFile,
  PullFromLDPlayer, ExtractAPK, InspectPair,
  SearchPair, ExportReport, DumpStrings, RunIl2CppDumper
} from '../wailsjs/go/main/App';

const state = {
  apk: '',
  hits: []
};

document.querySelector('#app').innerHTML = `
<div class="shell">
  <header>
    <div>
      <div class="eyebrow">TS ONLINE • IL2CPP</div>
      <h1>TS IL2CPP Research Tool</h1>
      <p>Trích xuất APK / LDPlayer, kiểm tra IL2CPP và tìm dấu vết class, method, chuỗi, ID.</p>
    </div>
    <div class="badge">v0.1</div>
  </header>

  <section class="card">
    <div class="section-title">
      <span>01</span><div><h2>Nguồn dữ liệu</h2><p>Kéo trực tiếp APK từ LDPlayer hoặc chọn APK có sẵn.</p></div>
    </div>
    <div class="grid three">
      <label>Thư mục LDPlayer<input id="ldRoot" value="F:\\LDPlayer\\LDPlayer9"></label>
      <label>Package<input id="packageName" value="com.vtcmobile.gz06"></label>
      <label>Thư mục xuất<input id="outputDir" value="F:\\LDPlayer\\LDPlayer9\\_ts_extract\\research_tool"></label>
    </div>
    <div class="actions">
      <button class="primary" id="pullBtn">Kéo từ LDPlayer + Extract</button>
      <button id="chooseApkBtn">Chọn APK</button>
      <button id="extractApkBtn">Extract APK đã chọn</button>
    </div>
    <div class="pathline"><b>APK:</b> <span id="apkPath">chưa chọn</span></div>
  </section>

  <section class="card">
    <div class="section-title">
      <span>02</span><div><h2>Cặp IL2CPP</h2><p>Dùng cặp cùng build để tránh sai mapping.</p></div>
    </div>
    <label>libil2cpp.so
      <div class="file-row"><input id="libPath" value="F:\\LDPlayer\\LDPlayer9\\_ts_extract\\DVT_native_arm64\\lib\\arm64-v8a\\libil2cpp.so"><button data-pick="lib">...</button></div>
    </label>
    <label>global-metadata.dat
      <div class="file-row"><input id="metadataPath" value="F:\\LDPlayer\\LDPlayer9\\_ts_extract\\DVT_native_arm64\\assets\\bin\\Data\\Managed\\Metadata\\global-metadata.dat"><button data-pick="meta">...</button></div>
    </label>
    <div class="actions">
      <button class="primary" id="inspectBtn">Kiểm tra cặp file</button>
    </div>
    <pre id="pairInfo" class="info">Chưa kiểm tra.</pre>
  </section>
  <section class="card">
    <div class="section-title">
      <span>03</span><div><h2>Tìm kiếm binary</h2><p>Tìm text hoặc số little-endian theo offset trong metadata và libil2cpp.</p></div>
    </div>
    <div class="search-row">
      <input id="queries" value="Compound, aDegree, bDegree, cDegree, 14005, 14011, 14019" placeholder="Compound, 14005, aDegree...">
      <select id="mode">
        <option value="auto">Auto</option>
        <option value="text">Text</option>
        <option value="u16">UInt16 LE</option>
        <option value="u32">UInt32 LE</option>
      </select>
      <button class="primary" id="searchBtn">Tìm</button>
      <button id="reportBtn">Xuất report</button>
    </div>
    <div id="summary" class="summary">Chưa tìm kiếm.</div>
    <div class="table-wrap">
      <table>
        <thead><tr><th>File</th><th>Query</th><th>Kiểu</th><th>Offset</th><th>Context</th></tr></thead>
        <tbody id="results"><tr><td colspan="5" class="empty">Chưa có dữ liệu.</td></tr></tbody>
      </table>
    </div>
  </section>

  <section class="card">
    <div class="section-title">
      <span>04</span><div><h2>Dump & Decompile</h2><p>Xuất toàn bộ chuỗi có offset hoặc chạy Il2CppDumper ngoài.</p></div>
    </div>
    <div class="actions">
      <button id="dumpStringsBtn">Dump Strings + Offset</button>
    </div>
    <div class="analyzer-grid">
      <label>Il2CppDumper.exe
        <div class="file-row"><input id="dumperPath" placeholder="Chọn Il2CppDumper.exe"><button data-pick="dumper">...</button></div>
      </label>
      <label>Thư mục output<input id="dumperOutput" value="F:\\LDPlayer\\LDPlayer9\\_ts_extract\\research_tool\\il2cppdumper-output"></label>
    </div>
    <div class="actions">
      <button class="primary" id="runDumperBtn">Chạy Il2CppDumper</button>
    </div>
    <pre id="dumperLog" class="info compact">Chưa chạy.</pre>
  </section>

  <footer><span id="status">Sẵn sàng.</span></footer>
</div>
`;
const $ = (id) => document.getElementById(id);
const status = (msg, error=false) => {
  $('status').textContent = msg;
  $('status').className = error ? 'error' : '';
};

const esc = (v='') => String(v)
  .replaceAll('&','&amp;').replaceAll('<','&lt;')
  .replaceAll('>','&gt;').replaceAll('"','&quot;');

function applyExtractResult(r) {
  if (!r) return;
  state.apk = r.APK || state.apk;
  $('apkPath').textContent = state.apk || 'chưa chọn';
  const base = r.OutputDir || '';
  if (base) {
    $('libPath').value = base + '\\libil2cpp.so';
    $('metadataPath').value = base + '\\global-metadata.dat';
  }
  status(`Extract xong: ${r.Architecture || ''} • ${(r.Files || []).length} file`);
}

$('chooseApkBtn').onclick = async () => {
  try {
    const p = await SelectAPK();
    if (p) {
      state.apk = p;
      $('apkPath').textContent = p;
      status('Đã chọn APK.');
    }
  } catch (e) { status(String(e), true); }
};

$('extractApkBtn').onclick = async () => {
  if (!state.apk) return status('Hãy chọn APK trước.', true);
  try {
    status('Đang giải nén APK...');
    const out = $('outputDir').value.trim() + '\\apk_extracted';
    applyExtractResult(await ExtractAPK(state.apk, out, 'arm64-v8a'));
  } catch (e) { status(String(e), true); }
};
$('pullBtn').onclick = async () => {
  try {
    status('Đang kéo APK từ LDPlayer...');
    const r = await PullFromLDPlayer(
      $('ldRoot').value.trim(),
      $('packageName').value.trim(),
      $('outputDir').value.trim()
    );
    applyExtractResult(r);
  } catch (e) { status(String(e), true); }
};

document.querySelector('[data-pick="lib"]').onclick = async () => {
  try { const p = await SelectFile('Chọn libil2cpp.so'); if (p) $('libPath').value = p; }
  catch (e) { status(String(e), true); }
};
document.querySelector('[data-pick="meta"]').onclick = async () => {
  try { const p = await SelectFile('Chọn global-metadata.dat'); if (p) $('metadataPath').value = p; }
  catch (e) { status(String(e), true); }
};

$('inspectBtn').onclick = async () => {
  try {
    status('Đang kiểm tra cặp IL2CPP...');
    const r = await InspectPair($('libPath').value.trim(), $('metadataPath').value.trim());
    $('pairInfo').textContent =
      `ELF: ${r.ELFClass} / ${r.ELFMachine}\n` +
      `Metadata: sanity=${r.MetadataSanity}, version=${r.MetadataVersion}\n` +
      `libil2cpp: ${fmtSize(r.Library.Size)}\nSHA256: ${r.Library.SHA256}\n` +
      `metadata: ${fmtSize(r.Metadata.Size)}\nSHA256: ${r.Metadata.SHA256}`;
    status('Cặp file đọc được bình thường.');
  } catch (e) { $('pairInfo').textContent = String(e); status(String(e), true); }
};
$('searchBtn').onclick = async () => {
  try {
    status('Đang quét binary...');
    const hits = await SearchPair(
      $('libPath').value.trim(),
      $('metadataPath').value.trim(),
      $('queries').value.trim(),
      $('mode').value
    );
    state.hits = hits || [];
    renderHits();
    status(`Tìm xong: ${state.hits.length} hit.`);
  } catch (e) { status(String(e), true); }
};

$('reportBtn').onclick = async () => {
  try {
    status('Đang tạo report...');
    const p = await ExportReport(
      $('libPath').value.trim(),
      $('metadataPath').value.trim(),
      $('queries').value.trim(),
      $('mode').value,
      ''
    );
    status('Đã xuất report: ' + p);
  } catch (e) { status(String(e), true); }
};

$('dumpStringsBtn').onclick = async () => {
  try {
    status('Đang dump chuỗi...');
    const files = await DumpStrings(
      $('libPath').value.trim(),
      $('metadataPath').value.trim(),
      ''
    );
    $('dumperLog').textContent = (files || []).join('\n');
    status('Đã dump chuỗi có offset.');
  } catch (e) { status(String(e), true); }
};

document.querySelector('[data-pick="dumper"]').onclick = async () => {
  try {
    const p = await SelectFile('Chọn Il2CppDumper.exe');
    if (p) $('dumperPath').value = p;
  } catch (e) { status(String(e), true); }
};

$('runDumperBtn').onclick = async () => {
  try {
    status('Đang chạy Il2CppDumper...');
    const log = await RunIl2CppDumper(
      $('dumperPath').value.trim(),
      $('libPath').value.trim(),
      $('metadataPath').value.trim(),
      $('dumperOutput').value.trim()
    );
    $('dumperLog').textContent = log || 'Hoàn tất.';
    status('Il2CppDumper hoàn tất.');
  } catch (e) {
    $('dumperLog').textContent = String(e);
    status(String(e), true);
  }
};

function renderHits() {
  $('summary').textContent = `${state.hits.length} kết quả (tối đa 600 hit mỗi lần quét).`;
  if (!state.hits.length) {
    $('results').innerHTML = '<tr><td colspan="5" class="empty">Không tìm thấy.</td></tr>';
    return;
  }
  $('results').innerHTML = state.hits.map(h => `
    <tr>
      <td>${esc(h.File)}</td>
      <td><b>${esc(h.Query)}</b></td>
      <td><span class="kind">${esc(h.Kind)}</span></td>
      <td><code>${esc(h.OffsetHex)}</code></td>
      <td class="preview"><code>${esc(h.Preview)}</code></td>
    </tr>
  `).join('');
}

function fmtSize(n) {
  if (!Number.isFinite(n)) return n;
  const units = ['B','KB','MB','GB'];
  let i = 0, v = n;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return v.toFixed(i ? 2 : 0) + ' ' + units[i];
}
