package main

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"debug/elf"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/wailsapp/wails/v2/pkg/runtime"
)

type App struct{ ctx context.Context }

type FileInfo struct {
	Name   string
	Path   string
	Size   int64
	SHA256 string
}
type ExtractResult struct {
	Source       string
	APK          string
	OutputDir    string
	Architecture string
	Files        []FileInfo
}

type PairInfo struct {
	Library         FileInfo
	Metadata        FileInfo
	ELFClass        string
	ELFMachine      string
	MetadataSanity  string
	MetadataVersion uint32
}

type SearchHit struct {
	File      string
	Query     string
	Kind      string
	Offset    int64
	OffsetHex string
	Preview   string
}

func NewApp() *App { return &App{} }

func (a *App) startup(ctx context.Context) {
	a.ctx = ctx
}
func (a *App) SelectAPK() (string, error) {
	return runtime.OpenFileDialog(a.ctx, runtime.OpenDialogOptions{
		Title:   "Chọn APK TS Online",
		Filters: []runtime.FileFilter{{DisplayName: "Android APK (*.apk)", Pattern: "*.apk"}},
	})
}

func (a *App) SelectFile(title string) (string, error) {
	return runtime.OpenFileDialog(a.ctx, runtime.OpenDialogOptions{Title: title})
}

func (a *App) SelectDirectory(title string) (string, error) {
	return runtime.OpenDirectoryDialog(a.ctx, runtime.OpenDialogOptions{Title: title})
}

func (a *App) PullFromLDPlayer(ldRoot, packageName, outputDir string) (ExtractResult, error) {
	if strings.TrimSpace(packageName) == "" {
		return ExtractResult{}, fmt.Errorf("package name đang trống")
	}
	adb := filepath.Join(ldRoot, "adb.exe")
	if _, err := os.Stat(adb); err != nil {
		return ExtractResult{}, fmt.Errorf("không tìm thấy adb.exe tại %s", adb)
	}
	if outputDir == "" {
		outputDir = filepath.Join(ldRoot, "_ts_extract", "research_tool")
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return ExtractResult{}, err
	}
	out, err := exec.Command(adb, "shell", "pm", "path", packageName).CombinedOutput()
	if err != nil {
		return ExtractResult{}, fmt.Errorf("adb pm path lỗi: %v\n%s", err, string(out))
	}
	var remote string
	for _, line := range strings.Split(string(out), "\n") {
		line = strings.TrimSpace(line)
		if strings.HasPrefix(line, "package:") && strings.HasSuffix(line, "base.apk") {
			remote = strings.TrimPrefix(line, "package:")
			break
		}
	}
	if remote == "" {
		return ExtractResult{}, fmt.Errorf("không tìm thấy base.apk của %s", packageName)
	}
	apkPath := filepath.Join(outputDir, strings.ReplaceAll(packageName, ".", "_")+"_base.apk")
	pullOut, err := exec.Command(adb, "pull", remote, apkPath).CombinedOutput()
	if err != nil {
		return ExtractResult{}, fmt.Errorf("adb pull lỗi: %v\n%s", err, string(pullOut))
	}
	result, err := a.ExtractAPK(apkPath, filepath.Join(outputDir, "extracted"), "arm64-v8a")
	result.Source = "LDPlayer: " + packageName
	return result, err
}

func (a *App) ExtractAPK(apkPath, outputDir, preferredArch string) (ExtractResult, error) {
	zr, err := zip.OpenReader(apkPath)
	if err != nil {
		return ExtractResult{}, err
	}
	defer zr.Close()
	arches := map[string]bool{}
	for _, f := range zr.File {
		parts := strings.Split(filepath.ToSlash(f.Name), "/")
		if len(parts) >= 3 && parts[0] == "lib" && parts[2] == "libil2cpp.so" {
			arches[parts[1]] = true
		}
	}
	arch := preferredArch
	if !arches[arch] {
		arch = ""
		if arches["arm64-v8a"] {
			arch = "arm64-v8a"
		}
		if arch == "" {
			keys := make([]string, 0, len(arches))
			for k := range arches {
				keys = append(keys, k)
			}
			sort.Strings(keys)
			if len(keys) > 0 {
				arch = keys[0]
			}
		}
	}
	if arch == "" {
		return ExtractResult{}, fmt.Errorf("APK không có libil2cpp.so")
	}
	if outputDir == "" {
		base := strings.TrimSuffix(filepath.Base(apkPath), filepath.Ext(apkPath))
		outputDir = filepath.Join(filepath.Dir(apkPath), base+"_il2cpp")
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return ExtractResult{}, err
	}

	wanted := map[string]string{
		"assets/bin/Data/Managed/Metadata/global-metadata.dat": filepath.Join(outputDir, "global-metadata.dat"),
		"lib/" + arch + "/libil2cpp.so":                        filepath.Join(outputDir, "libil2cpp.so"),
		"lib/" + arch + "/libunity.so":                         filepath.Join(outputDir, "libunity.so"),
	}
	extracted := []FileInfo{}
	for _, f := range zr.File {
		dst, ok := wanted[filepath.ToSlash(f.Name)]
		if !ok {
			continue
		}
		if err := copyZipFile(f, dst); err != nil {
			return ExtractResult{}, err
		}
		info, err := makeFileInfo(dst)
		if err != nil {
			return ExtractResult{}, err
		}
		extracted = append(extracted, info)
	}
	if !fileExists(filepath.Join(outputDir, "libil2cpp.so")) ||
		!fileExists(filepath.Join(outputDir, "global-metadata.dat")) {
		return ExtractResult{}, fmt.Errorf("thiếu libil2cpp.so hoặc global-metadata.dat")
	}
	return ExtractResult{
		Source:       "APK",
		APK:          apkPath,
		OutputDir:    outputDir,
		Architecture: arch,
		Files:        extracted,
	}, nil
}

func copyZipFile(f *zip.File, dst string) error {
	src, err := f.Open()
	if err != nil {
		return err
	}
	defer src.Close()
	if err := os.MkdirAll(filepath.Dir(dst), 0755); err != nil {
		return err
	}
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, src)
	return err
}
func (a *App) InspectPair(libPath, metadataPath string) (PairInfo, error) {
	libInfo, err := makeFileInfo(libPath)
	if err != nil {
		return PairInfo{}, err
	}
	metaInfo, err := makeFileInfo(metadataPath)
	if err != nil {
		return PairInfo{}, err
	}

	ef, err := elf.Open(libPath)
	if err != nil {
		return PairInfo{}, fmt.Errorf("ELF lỗi: %v", err)
	}
	class := ef.Class.String()
	machine := ef.Machine.String()
	ef.Close()

	head := make([]byte, 8)
	mf, err := os.Open(metadataPath)
	if err != nil {
		return PairInfo{}, err
	}
	_, err = io.ReadFull(mf, head)
	mf.Close()
	if err != nil {
		return PairInfo{}, err
	}

	sanity := binary.LittleEndian.Uint32(head[:4])
	version := binary.LittleEndian.Uint32(head[4:8])
	return PairInfo{
		Library:         libInfo,
		Metadata:        metaInfo,
		ELFClass:        class,
		ELFMachine:      machine,
		MetadataSanity:  fmt.Sprintf("0x%08X", sanity),
		MetadataVersion: version,
	}, nil
}

func (a *App) SearchPair(libPath, metadataPath, rawQueries, mode string) ([]SearchHit, error) {
	paths := []string{metadataPath, libPath}
	queries := splitQueries(rawQueries)
	if len(queries) == 0 {
		return nil, fmt.Errorf("chưa nhập từ khóa/ID")
	}
	hits := make([]SearchHit, 0)
	const maxHits = 600
	for _, path := range paths {
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, err
		}
		for _, q := range queries {
			patterns, err := buildPatterns(q, mode)
			if err != nil {
				continue
			}
			for kind, pattern := range patterns {
				if len(pattern) == 0 {
					continue
				}
				searchData := data
				searchPattern := pattern
				if kind == "text" {
					searchData = bytes.ToLower(data)
					searchPattern = bytes.ToLower(pattern)
				}
				start := 0
				for len(hits) < maxHits {
					idx := bytes.Index(searchData[start:], searchPattern)
					if idx < 0 {
						break
					}
					off := start + idx
					hits = append(hits, SearchHit{
						File:      filepath.Base(path),
						Query:     q,
						Kind:      kind,
						Offset:    int64(off),
						OffsetHex: fmt.Sprintf("0x%X", off),
						Preview:   makePreview(data, off, len(pattern)),
					})
					start = off + 1
					if start >= len(data) {
						break
					}
				}
			}
		}
	}
	sort.Slice(hits, func(i, j int) bool {
		if hits[i].File != hits[j].File {
			return hits[i].File < hits[j].File
		}
		return hits[i].Offset < hits[j].Offset
	})
	return hits, nil
}

func buildPatterns(q, mode string) (map[string][]byte, error) {
	out := map[string][]byte{}
	m := strings.ToLower(strings.TrimSpace(mode))
	if m == "" || m == "auto" || m == "text" {
		out["text"] = []byte(q)
	}
	if m == "auto" || m == "u16" || m == "u32" {
		n, err := strconv.ParseUint(strings.TrimSpace(q), 0, 32)
		if err == nil {
			if (m == "auto" || m == "u16") && n <= 0xFFFF {
				b := make([]byte, 2)
				binary.LittleEndian.PutUint16(b, uint16(n))
				out["u16-le"] = b
			}
			if m == "auto" || m == "u32" {
				b := make([]byte, 4)
				binary.LittleEndian.PutUint32(b, uint32(n))
				out["u32-le"] = b
			}
		} else if m != "auto" && m != "text" {
			return nil, err
		}
	}
	return out, nil
}
func splitQueries(s string) []string {
	fields := strings.FieldsFunc(s, func(r rune) bool {
		return r == ',' || r == ';' || r == '\n' || r == '\r'
	})
	out := make([]string, 0, len(fields))
	for _, f := range fields {
		f = strings.TrimSpace(f)
		if f != "" {
			out = append(out, f)
		}
	}
	return out
}

func makePreview(data []byte, off, matchLen int) string {
	start := off - 24
	if start < 0 {
		start = 0
	}
	end := off + matchLen + 40
	if end > len(data) {
		end = len(data)
	}
	chunk := data[start:end]
	display := make([]byte, len(chunk))
	for i, b := range chunk {
		if b >= 32 && b <= 126 {
			display[i] = b
		} else {
			display[i] = '.'
		}
	}
	return fmt.Sprintf("%s | hex:%s", string(display), hex.EncodeToString(chunk))
}

func makeFileInfo(path string) (FileInfo, error) {
	st, err := os.Stat(path)
	if err != nil {
		return FileInfo{}, err
	}
	f, err := os.Open(path)
	if err != nil {
		return FileInfo{}, err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return FileInfo{}, err
	}
	return FileInfo{
		Name: filepath.Base(path), Path: path, Size: st.Size(),
		SHA256: fmt.Sprintf("%x", h.Sum(nil)),
	}, nil
}
func (a *App) ExportReport(libPath, metadataPath, rawQueries, mode, outputPath string) (string, error) {
	info, err := a.InspectPair(libPath, metadataPath)
	if err != nil {
		return "", err
	}
	hits, err := a.SearchPair(libPath, metadataPath, rawQueries, mode)
	if err != nil {
		return "", err
	}
	if outputPath == "" {
		outputPath = filepath.Join(filepath.Dir(metadataPath),
			"research-report-"+time.Now().Format("20060102-150405")+".md")
	}
	var b strings.Builder
	fmt.Fprintf(&b, "# TS IL2CPP Research Report\n\n")
	fmt.Fprintf(&b, "- ELF: %s / %s\n", info.ELFClass, info.ELFMachine)
	fmt.Fprintf(&b, "- Metadata sanity: %s\n", info.MetadataSanity)
	fmt.Fprintf(&b, "- Metadata version: %d\n", info.MetadataVersion)
	fmt.Fprintf(&b, "- libil2cpp SHA256: %s\n", info.Library.SHA256)
	fmt.Fprintf(&b, "- metadata SHA256: %s\n", info.Metadata.SHA256)
	fmt.Fprintf(&b, "- Queries: %s\n\n", rawQueries)
	fmt.Fprintf(&b, "## Hits (%d)\n\n", len(hits))
	for _, h := range hits {
		fmt.Fprintf(&b, "- **%s** %q %s %s — %q\n",
			h.File, h.Query, h.Kind, h.OffsetHex, h.Preview)
	}
	if err := os.WriteFile(outputPath, []byte(b.String()), 0644); err != nil {
		return "", err
	}
	return outputPath, nil
}

func fileExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}

func (a *App) DumpStrings(libPath, metadataPath, outputDir string) ([]string, error) {
	if outputDir == "" {
		outputDir = filepath.Join(filepath.Dir(metadataPath), "string-dumps")
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return nil, err
	}
	inputs := []string{metadataPath, libPath}
	outputs := make([]string, 0, len(inputs))
	for _, path := range inputs {
		data, err := os.ReadFile(path)
		if err != nil {
			return nil, err
		}
		name := strings.TrimSuffix(filepath.Base(path), filepath.Ext(path))
		outPath := filepath.Join(outputDir, name+"-strings.txt")
		content := dumpASCIIStrings(data, 4)
		if err := os.WriteFile(outPath, []byte(content), 0644); err != nil {
			return nil, err
		}
		outputs = append(outputs, outPath)
	}
	return outputs, nil
}

func dumpASCIIStrings(data []byte, minLen int) string {
	var out strings.Builder
	start := -1
	flush := func(end int) {
		if start >= 0 && end-start >= minLen {
			fmt.Fprintf(&out, "0x%X\t%s\n", start, string(data[start:end]))
		}
		start = -1
	}
	for i, b := range data {
		if b >= 32 && b <= 126 {
			if start < 0 {
				start = i
			}
		} else {
			flush(i)
		}
	}
	flush(len(data))
	return out.String()
}

func (a *App) RunIl2CppDumper(exePath, libPath, metadataPath, outputDir string) (string, error) {
	if !fileExists(exePath) {
		return "", fmt.Errorf("không tìm thấy Il2CppDumper.exe")
	}
	if outputDir == "" {
		outputDir = filepath.Join(filepath.Dir(metadataPath), "il2cppdumper-output")
	}
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return "", err
	}
	cmd := exec.Command(exePath, libPath, metadataPath, outputDir)
	cmd.Dir = filepath.Dir(exePath)
	out, err := cmd.CombinedOutput()
	logText := string(out)
	if err != nil {
		return logText, fmt.Errorf("Il2CppDumper lỗi: %v\n%s", err, logText)
	}
	return logText, nil
}
