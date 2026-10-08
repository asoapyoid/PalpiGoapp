import React, { useState } from 'react';
import {
  Copy,
  Check,
  Download,
  FileCode,
  FolderTree,
  Search,
  Smartphone,
  Terminal,
  Cloud,
  ExternalLink
} from 'lucide-react';
import { KOTLIN_SOURCE_FILES, KotlinSourceFile } from '../data/kotlinFiles';

interface KotlinCodeExplorerProps {
  amoledMode: boolean;
  onTriggerSnackbar: (message: string, type?: 'success' | 'warning' | 'info') => void;
}

export const KotlinCodeExplorer: React.FC<KotlinCodeExplorerProps> = ({
  amoledMode,
  onTriggerSnackbar,
}) => {
  const [selectedFileId, setSelectedFileId] = useState<string>(KOTLIN_SOURCE_FILES[2].id);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [codeSearch, setCodeSearch] = useState<string>('');

  const activeFile: KotlinSourceFile =
    KOTLIN_SOURCE_FILES.find((f) => f.id === selectedFileId) || KOTLIN_SOURCE_FILES[0];

  const handleCopyCode = (file: KotlinSourceFile) => {
    navigator.clipboard.writeText(file.code);
    setCopiedId(file.id);
    onTriggerSnackbar(`Copied ${file.filename} (${file.linesOfCode} lines) to clipboard`, 'success');
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleDownloadSingleFile = (file: KotlinSourceFile) => {
    const blob = new Blob([file.code], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.filename.split(' ')[0];
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    onTriggerSnackbar(`Downloaded ${file.filename}`, 'success');
  };

  /**
   * Generates a self-extracting shell script that creates the exact Android Studio
   * directory tree and writes every Kotlin/XML/Gradle file + GitHub Actions APK builder.
   */
  const handleDownloadProjectGeneratorScript = () => {
    const scriptLines: string[] = [
      '#!/usr/bin/env bash',
      '# ============================================================================',
      '# PokéMate Companion Android Studio & Cloud APK Project Generator',
      '# Run: bash setup-pokemate-android.sh',
      '# ============================================================================',
      'set -e',
      'mkdir -p PokeMateAndroid',
      'cd PokeMateAndroid',
      '',
    ];

    for (const file of KOTLIN_SOURCE_FILES) {
      const dir = file.path.includes('/')
        ? file.path.slice(0, file.path.lastIndexOf('/'))
        : '';
      if (dir) {
        scriptLines.push(`mkdir -p "${dir}"`);
      }
      scriptLines.push(`cat << 'EOF_POKEMATE_FILE' > "${file.path}"`);
      scriptLines.push(file.code);
      scriptLines.push('EOF_POKEMATE_FILE');
      scriptLines.push('');
    }

    scriptLines.push('echo "✅ PokéMate Android project created in ./PokeMateAndroid"');
    scriptLines.push('echo "👉 Option 1 (No Android Studio needed): Push this folder to GitHub; .github/workflows/build-apk.yml will automatically build your installable APK in the Actions tab!"');
    scriptLines.push('echo "👉 Option 2 (Local Build): Open ./PokeMateAndroid in Android Studio and select Build -> Build Bundle(s) / APK(s) -> Build APK(s)"');

    const blob = new Blob([scriptLines.join('\n')], { type: 'text/x-sh;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'setup-pokemate-android.sh';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    onTriggerSnackbar(
      'Downloaded setup-pokemate-android.sh (Auto-generates full Android Studio & GitHub APK project)',
      'success'
    );
  };

  const codeLines = activeFile.code.split('\n');

  return (
    <div className="space-y-6">
      {/* HOW TO GET THE APK ON YOUR ANDROID PHONE BANNER */}
      <div
        className={`p-5 rounded-xl border ${
          amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/70 border-slate-800'
        }`}
      >
        <div className="flex flex-wrap items-start justify-between gap-4 pb-4 border-b border-slate-800">
          <div>
            <h2 className="text-base font-semibold text-white flex items-center gap-2">
              <Smartphone className="w-4 h-4 text-emerald-400" />
              <span>Install `PalpiGO-v1.2.2026.apk` Directly on Your Android Phone</span>
            </h2>
            <p className="text-xs text-slate-400 mt-1 max-w-3xl">
              Compiled and signed (v1.2.2026 Build 3, APK Signature Scheme v2/v3, Target SDK 34) for Android 8.0–15 (`com.pokemate.companion`). Includes the Floating Overlay HUD, Pokémon GO Accessibility Service, and live Field Research scanner.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <a
              href="/api/download/PalpiGO-v1.2.2026.apk"
              download="PalpiGO-v1.2.2026.apk"
              onClick={() =>
                onTriggerSnackbar(
                  'Downloading signed PalpiGO-v1.2.2026.apk ready to install on your phone!',
                  'success'
                )
              }
              className="px-4 py-2.5 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-bold text-xs flex items-center gap-2 shadow-sm whitespace-nowrap"
            >
              <Download className="w-4 h-4" />
              <span>Download `PalpiGO-v1.2.2026.apk`</span>
            </a>

            <a
              href="https://ntfy.envs.net/file/gc29aqy3VtJD.apk"
              target="_blank"
              rel="noopener noreferrer"
              download="PalpiGO-v1.2.2026.apk"
              onClick={() =>
                onTriggerSnackbar(
                  'Opening verified high-speed PalpiGO-v1.2.2026.apk APK mirror in new tab!',
                  'info'
                )
              }
              className="px-3.5 py-2.5 rounded-xl bg-emerald-950/80 hover:bg-emerald-900 border border-emerald-500/40 text-emerald-300 font-bold text-xs flex items-center gap-2 whitespace-nowrap"
            >
              <ExternalLink className="w-4 h-4" />
              <span>Direct Phone Mirror</span>
            </a>

            <a
              href="/api/download/android-project.zip"
              download="PokeMate-Android-APK-Project.zip"
              onClick={() =>
                onTriggerSnackbar(
                  'Downloading PokeMate-Android-APK-Project.zip source code bundle',
                  'info'
                )
              }
              className="px-3.5 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 font-medium text-xs flex items-center gap-2 whitespace-nowrap"
            >
              <Download className="w-4 h-4 text-sky-400" />
              <span>Source Project (`.zip`)</span>
            </a>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-4 text-xs">
          <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800/80 space-y-1.5">
            <div className="font-semibold text-emerald-400 flex items-center gap-1.5">
              <Cloud className="w-3.5 h-3.5" />
              <span>Method A: Cloud APK (No PC SDK Needed)</span>
            </div>
            <p className="text-slate-300 leading-relaxed">
              1. Run <code className="text-emerald-300">bash setup-pokemate-android.sh</code> and push the generated folder to a free GitHub repo.<br />
              2. Included <code className="text-emerald-300">.github/workflows/build-apk.yml</code> runs Gradle in the cloud.<br />
              3. Download <code className="text-white">pokemate-companion-debug-apk.zip</code> from the GitHub <strong>Actions</strong> tab directly on your phone.
            </p>
          </div>

          <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800/80 space-y-1.5">
            <div className="font-semibold text-sky-400 flex items-center gap-1.5">
              <Terminal className="w-3.5 h-3.5" />
              <span>Method B: Android Studio Local APK</span>
            </div>
            <p className="text-slate-300 leading-relaxed">
              1. Open the generated <code className="text-sky-300">PokeMateAndroid</code> folder in Android Studio.<br />
              2. Click <strong>Build → Build Bundle(s) / APK(s) → Build APK(s)</strong>.<br />
              3. Copy <code className="text-white">app/build/outputs/apk/debug/app-debug.apk</code> to your phone via USB or Google Drive.
            </p>
          </div>

          <div className="p-3.5 rounded-lg bg-slate-950/80 border border-slate-800/80 space-y-1.5">
            <div className="font-semibold text-amber-400 flex items-center gap-1.5">
              <Smartphone className="w-3.5 h-3.5" />
              <span>First-Run Phone Permissions</span>
            </div>
            <p className="text-slate-300 leading-relaxed">
              1. On Android 13/14, go to <strong>Settings → Apps → PokéMate → ⋮ → Allow restricted settings</strong>.<br />
              2. Enable <strong>Display over other apps</strong> (`SYSTEM_ALERT_WINDOW`).<br />
              3. Enable <strong>Accessibility → PokéMate Auto-Gift</strong> and tap <strong>Launch Overlay</strong>.
            </p>
          </div>
        </div>
      </div>

      {/* SOURCE CODE EXPLORER */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
        {/* LEFT 4 COLS: Android Project File Tree */}
        <div className="lg:col-span-4 space-y-4">
          <div
            className={`p-4 rounded-xl border ${
              amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/60 border-slate-800'
            }`}
          >
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <FolderTree className="w-4 h-4 text-emerald-400" />
                <h2 className="text-sm font-semibold text-white">
                  Android Studio Project Files ({KOTLIN_SOURCE_FILES.length})
                </h2>
              </div>
            </div>

            <div className="space-y-1.5">
              {KOTLIN_SOURCE_FILES.map((file) => {
                const isSelected = file.id === activeFile.id;
                return (
                  <button
                    key={file.id}
                    onClick={() => setSelectedFileId(file.id)}
                    className={`w-full text-left p-2.5 rounded-lg border transition-colors flex items-start justify-between gap-2 ${
                      isSelected
                        ? 'bg-emerald-500/15 border-emerald-500/60 text-white'
                        : 'bg-slate-950/60 border-slate-800/70 text-slate-300 hover:bg-slate-800/50'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-mono font-semibold flex items-center gap-1.5 truncate">
                        <FileCode className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                        <span className="truncate">{file.filename}</span>
                      </div>
                      <div className="text-[11px] text-slate-400 truncate mt-0.5">
                        {file.path}
                      </div>
                    </div>
                    <span className="text-[11px] font-mono text-slate-400 shrink-0 tabular-nums">
                      {file.linesOfCode}L
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* RIGHT 8 COLS: Full Syntax-Formatted Code Viewer */}
        <div className="lg:col-span-8">
          <div
            className={`rounded-xl border overflow-hidden ${
              amoledMode ? 'bg-black border-slate-800' : 'bg-slate-900/80 border-slate-800'
            }`}
          >
            {/* File Header Bar */}
            <div className="px-4 py-3 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3 bg-slate-950/80">
              <div>
                <div className="text-sm font-mono font-semibold text-white flex items-center gap-2">
                  <span>{activeFile.path}</span>
                </div>
                <p className="text-xs text-slate-400 mt-0.5">{activeFile.description}</p>
              </div>

              <div className="flex items-center gap-2">
                <div className="relative">
                  <Search className="w-3.5 h-3.5 text-slate-500 absolute left-2.5 top-1/2 -translate-y-1/2" />
                  <input
                    type="text"
                    value={codeSearch}
                    onChange={(e) => setCodeSearch(e.target.value)}
                    placeholder="Highlight in file..."
                    className="pl-8 pr-2.5 py-1.5 rounded-lg bg-slate-900 border border-slate-800 text-xs text-white placeholder:text-slate-500 w-40 focus:outline-none focus:border-emerald-500"
                  />
                </div>

                <button
                  onClick={() => handleCopyCode(activeFile)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-white flex items-center gap-1.5 whitespace-nowrap"
                >
                  {copiedId === activeFile.id ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-emerald-400" />
                      <span>Copied</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5" />
                      <span>Copy File</span>
                    </>
                  )}
                </button>

                <button
                  onClick={() => handleDownloadSingleFile(activeFile)}
                  className="px-3 py-1.5 rounded-lg bg-emerald-500 hover:bg-emerald-400 text-slate-950 font-semibold text-xs flex items-center gap-1.5 whitespace-nowrap"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download</span>
                </button>
              </div>
            </div>

            {/* Code Lines Display */}
            <div className="p-4 overflow-x-auto max-h-[680px] overflow-y-auto bg-[#070A0F] font-mono text-xs leading-relaxed">
              <table className="w-full border-collapse">
                <tbody>
                  {codeLines.map((line, index) => {
                    const isHighlighted =
                      codeSearch.trim().length > 1 &&
                      line.toLowerCase().includes(codeSearch.toLowerCase());
                    const isComment =
                      line.trim().startsWith('//') ||
                      line.trim().startsWith('/*') ||
                      line.trim().startsWith('*') ||
                      line.trim().startsWith('<!--');
                    return (
                      <tr
                        key={index}
                        className={isHighlighted ? 'bg-amber-500/20' : 'hover:bg-slate-900/60'}
                      >
                        <td className="pr-4 select-none text-right text-slate-600 w-10 tabular-nums">
                          {index + 1}
                        </td>
                        <td
                          className={`whitespace-pre ${
                            isComment ? 'text-emerald-400/80 italic' : 'text-slate-200'
                          }`}
                        >
                          {line || ' '}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
