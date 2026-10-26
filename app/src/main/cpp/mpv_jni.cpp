// JNI bridge for KiduyuTV. Uses the stable libmpv client API from mpv v0.40.0.
// The matching client header is vendored from https://github.com/mpv-player/mpv
// at tag v0.40.0. mpv's client.h is ISC-style licensed; see its copyright header.
#include <jni.h>
#include <mpv/client.h>
#include <atomic>
#include <cmath>
#include <clocale>
#include <cstdint>
#include <limits>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

namespace {
struct Session {
    mpv_handle* mpv = nullptr;
    jobject surface = nullptr;
    std::string error;
    bool fileLoaded = false;
    std::vector<std::pair<std::string, std::string>> pendingSubtitles;
};
Session* from(jlong p) { return reinterpret_cast<Session*>(static_cast<intptr_t>(p)); }
std::string str(JNIEnv* e, jstring s) {
    if (!s) return {};
    const char* p = e->GetStringUTFChars(s, nullptr);
    std::string out = p ? p : "";
    if (p) e->ReleaseStringUTFChars(s, p);
    return out;
}
std::string json(const char* s) {
    std::string o = "\"";
    if (s) for (unsigned char c : std::string(s)) {
        switch (c) { case '"': o += "\\\""; break; case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break; case '\r': o += "\\r"; break; case '\t': o += "\\t"; break;
            default: if (c >= 32) o += static_cast<char>(c); }
    }
    return o + '"';
}
int command(Session* s, const std::vector<std::string>& args) {
    if (!s || !s->mpv) return MPV_ERROR_INVALID_PARAMETER;
    std::vector<const char*> argv;
    for (const auto& a : args) argv.push_back(a.c_str());
    argv.push_back(nullptr);
    int result = mpv_command(s->mpv, argv.data());
    if (result < 0) s->error = mpv_error_string(result);
    return result;
}
bool get_double(mpv_handle* h, const char* name, double* v) {
    return mpv_get_property(h, name, MPV_FORMAT_DOUBLE, v) >= 0;
}
long long milliseconds(double seconds) {
    if (!std::isfinite(seconds) || seconds <= 0.0) return 0;
    const double maximum = static_cast<double>(std::numeric_limits<long long>::max()) / 1000.0;
    if (seconds >= maximum) return std::numeric_limits<long long>::max();
    return static_cast<long long>(seconds * 1000.0);
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeCreate(JNIEnv*, jobject) {
    std::setlocale(LC_NUMERIC, "C");
    auto* s = new Session();
    s->mpv = mpv_create();
    if (!s->mpv) { delete s; return 0; }
    const char* opts[][2] = {{"vo","gpu"},{"gpu-context","android"},{"opengl-es","yes"},{"gpu-api","opengl"},{"ao","audiotrack"},
        {"input-default-bindings","no"},{"input-vo-keyboard","no"},{"terminal","no"},
        {"idle","once"},{"hwdec","auto-safe"},{"cache","yes"},
        {"demuxer-readahead-secs","20"},{"cache-secs","30"},{"keep-open","yes"}};
    for (const auto& o : opts) {
        if (mpv_set_option_string(s->mpv, o[0], o[1]) < 0) {
            mpv_terminate_destroy(s->mpv); delete s; return 0;
        }
    }
    if (mpv_initialize(s->mpv) < 0) { mpv_terminate_destroy(s->mpv); delete s; return 0; }
    mpv_observe_property(s->mpv, 1, "time-pos", MPV_FORMAT_DOUBLE);
    mpv_observe_property(s->mpv, 2, "duration", MPV_FORMAT_DOUBLE);
    mpv_observe_property(s->mpv, 3, "pause", MPV_FORMAT_FLAG);
    mpv_observe_property(s->mpv, 4, "paused-for-cache", MPV_FORMAT_FLAG);
    mpv_observe_property(s->mpv, 5, "eof-reached", MPV_FORMAT_FLAG);
    mpv_observe_property(s->mpv, 6, "track-list", MPV_FORMAT_NODE);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(s));
}
extern "C" JNIEXPORT jboolean JNICALL
Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeAttach(JNIEnv* e, jobject, jlong p, jobject surface) {
    Session* s = from(p);
    if (!s || !s->mpv || !surface || s->surface) return JNI_FALSE;
    s->surface = e->NewGlobalRef(surface);
    if (!s->surface) return JNI_FALSE;
    // Follow mpv-android's Android VO lifecycle: set the Java Surface as `wid`
    // after initialization so the Android video output can bind to it.
    int64_t wid = static_cast<int64_t>(reinterpret_cast<intptr_t>(s->surface));
    int result = mpv_set_option(s->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0) {
        e->DeleteGlobalRef(s->surface);
        s->surface = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeDetach(JNIEnv* e, jobject, jlong p) {
    Session* s = from(p);
    if (!s || !s->mpv || !s->surface) return;
    int64_t wid = 0;
    int result = mpv_set_option(s->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0) s->error = std::string("Could not detach libmpv surface: ") + mpv_error_string(result);
    e->DeleteGlobalRef(s->surface);
    s->surface = nullptr;
}
extern "C" JNIEXPORT void JNICALL
Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeLoad(JNIEnv* e,jobject,jlong p,jstring url,jobject headers,jobject subtitles,jlong start) {
    Session* s=from(p); if (!s || !s->mpv) return;
    // A structured map is converted to mpv's escaped option-list syntax, never a shell command.
    std::string fields;
    jclass mapc=e->GetObjectClass(headers); jmethodID entrySet=e->GetMethodID(mapc,"entrySet","()Ljava/util/Set;");
    jobject set=e->CallObjectMethod(headers,entrySet); jclass setc=e->GetObjectClass(set);
    jobjectArray entries=(jobjectArray)e->CallObjectMethod(set,setc?e->GetMethodID(setc,"toArray","()[Ljava/lang/Object;"):nullptr);
    if(entries) for(jsize i=0;i<e->GetArrayLength(entries);++i) {
        jobject ent=e->GetObjectArrayElement(entries,i); jclass ec=e->GetObjectClass(ent);
        jobject k=e->CallObjectMethod(ent,e->GetMethodID(ec,"getKey","()Ljava/lang/Object;"));
        jobject v=e->CallObjectMethod(ent,e->GetMethodID(ec,"getValue","()Ljava/lang/Object;"));
        std::string key=str(e,(jstring)k), val=str(e,(jstring)v);
        if(key.empty() || key.find_first_of("\r\n:")!=std::string::npos || val.find_first_of("\r\n")!=std::string::npos) { s->error = "Invalid HTTP header supplied to libmpv"; return; }
        if(!fields.empty()) fields+=',';
        fields+=key+": "; for(char c:val) { if(c==',' || c=='\\') fields+='\\'; fields+=c; }
    }
    {
        int headerResult = mpv_set_property_string(s->mpv, "http-header-fields", fields.c_str());
        if (headerResult < 0) { s->error = std::string("libmpv rejected HTTP headers: ") + mpv_error_string(headerResult); return; }
    }
    s->error.clear();
    s->fileLoaded = false;
    s->pendingSubtitles.clear();
    jclass lc=e->GetObjectClass(subtitles); jmethodID size=e->GetMethodID(lc,"size","()I"), get=e->GetMethodID(lc,"get","(I)Ljava/lang/Object;");
    jint n=e->CallIntMethod(subtitles,size);
    for(jint i=0;i<n;++i) {
        jobject pair=e->CallObjectMethod(subtitles,get,i); jclass pc=e->GetObjectClass(pair);
        auto first=(jstring)e->GetObjectField(pair,e->GetFieldID(pc,"first","Ljava/lang/Object;"));
        auto second=(jstring)e->GetObjectField(pair,e->GetFieldID(pc,"second","Ljava/lang/Object;"));
        s->pendingSubtitles.emplace_back(str(e,first),str(e,second));
    }
    command(s,{"loadfile",str(e,url),"replace","-1","start="+std::to_string(static_cast<double>(start)/1000.0)});
}
extern "C" JNIEXPORT void JNICALL Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeCommand(JNIEnv* e,jobject,jlong p,jobjectArray a) {
    Session* s=from(p); if(!s||!s->mpv)return; std::vector<std::string> v;
    for(jsize i=0;i<e->GetArrayLength(a);++i) v.push_back(str(e,(jstring)e->GetObjectArrayElement(a,i)));
    command(s,v);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeSnapshot(JNIEnv* e,jobject,jlong p) {
    Session* s=from(p); if(!s||!s->mpv)return e->NewStringUTF("{}");
    // Drain the client event queue on the IO polling thread; this keeps native
    // event handling off the UI thread and records asynchronous load failures.
    for (;;) {
        mpv_event* event = mpv_wait_event(s->mpv, 0.0);
        if (!event || event->event_id == MPV_EVENT_NONE) break;
        if (event->event_id == MPV_EVENT_END_FILE && event->data) {
            auto* end = static_cast<mpv_event_end_file*>(event->data);
            if (end->error < 0) s->error = mpv_error_string(end->error);
        }
        if (event->event_id == MPV_EVENT_START_FILE) {
            s->error.clear();
            s->fileLoaded = false;
        }
        if (event->event_id == MPV_EVENT_FILE_LOADED) {
            s->fileLoaded = true;
            for (const auto& subtitle : s->pendingSubtitles)
                command(s,{"sub-add",subtitle.first,"select",subtitle.second});
            s->pendingSubtitles.clear();
        }
    }
    double pos=0,dur=0; get_double(s->mpv,"time-pos",&pos); get_double(s->mpv,"duration",&dur);
    int pause=1,buffer=0,eof=0; mpv_get_property(s->mpv,"pause",MPV_FORMAT_FLAG,&pause);
    mpv_get_property(s->mpv,"paused-for-cache",MPV_FORMAT_FLAG,&buffer); mpv_get_property(s->mpv,"eof-reached",MPV_FORMAT_FLAG,&eof);
    std::ostringstream o; o<<"{\"positionMs\":"<<milliseconds(pos)<<",\"durationMs\":"<<milliseconds(dur)
      <<",\"loading\":"<<(s->fileLoaded?"false":"true")
      <<",\"paused\":"<<(pause?"true":"false")<<",\"buffering\":"<<(buffer?"true":"false")<<",\"eof\":"<<(eof?"true":"false")<<",\"error\":"<<(s->error.empty()?"null":json(s->error.c_str()))<<",\"tracks\":[";
    mpv_node node{};
    if(mpv_get_property(s->mpv,"track-list",MPV_FORMAT_NODE,&node)>=0 && node.format==MPV_FORMAT_NODE_ARRAY) {
        bool comma=false; mpv_node_list* list=node.u.list;
        for(int i=0;i<list->num;++i) { mpv_node& item=list->values[i]; if(item.format!=MPV_FORMAT_NODE_MAP)continue;
            auto field=[&](const char* name)->mpv_node* { for(int j=0;j<item.u.list->num;++j)if(std::string(item.u.list->keys[j])==name)return &item.u.list->values[j];return nullptr;};
            auto id=field("id"),type=field("type"),title=field("title"),lang=field("lang"),sel=field("selected"),ext=field("external");
            if(!id||!type||type->format!=MPV_FORMAT_STRING) continue;
            if(comma) o<<',';
            comma=true;
            const char* typ=type->u.string; const char* label=(title&&title->format==MPV_FORMAT_STRING)?title->u.string:typ;
            const char* language=(lang&&lang->format==MPV_FORMAT_STRING)?lang->u.string:"";
            o<<"{\"id\":"<<id->u.int64<<",\"type\":"<<json(typ)<<",\"label\":"<<json(label)<<",\"language\":"<<(language[0]?json(language):"null")<<",\"selected\":"<<(sel&&sel->format==MPV_FORMAT_FLAG&&sel->u.flag?"true":"false")<<",\"external\":"<<(ext&&ext->format==MPV_FORMAT_FLAG&&ext->u.flag?"true":"false")<<'}';
        }
        mpv_free_node_contents(&node);
    }
    o<<"]}"; return e->NewStringUTF(o.str().c_str());
}
extern "C" JNIEXPORT void JNICALL Java_com_kiduyuk_klausk_kiduyutv_ui_player_directstream_playback_LibMpvSession_nativeRelease(JNIEnv* e,jobject,jlong p) {
    Session* s=from(p); if(!s)return; if(s->mpv)mpv_terminate_destroy(s->mpv); if(s->surface)e->DeleteGlobalRef(s->surface); delete s;
}
