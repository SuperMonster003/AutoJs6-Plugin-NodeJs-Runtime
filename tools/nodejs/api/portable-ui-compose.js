  function __autojs6_create_ui(__autojs6_call_autojs, __autojs6_bridge_attach_events = () => null, __autojs6_family_call = __autojs6_call_autojs) {
    const writableViewProperties = ["text", "hint", "textSize", "textColor", "textStyle", "singleLine", "maxLines", "inputType",
      "checked", "enabled", "visible", "visibility", "alpha", "contentDescription", "clickable", "focusable",
      "width", "height", "padding", "margin", "backgroundColor", "gravity", "layoutGravity", "weight", "orientation",
      "min", "max", "progress", "indeterminate", "rating", "numStars", "stepSize", "isIndicator",
      "items", "selectedIndex", "checkedId", "textOn", "textOff", "value", "wrapSelectorWheel", "displayedValues",
      "cardCornerRadius", "cardElevation", "src", "scaleType", "adjustViewBounds",
      "url", "html", "javaScriptEnabled", "domStorageEnabled", "title", "subtitle",
      "navigationIcon", "navigationContentDescription", "menu", "drawerLockMode", "currentItem", "offscreenPageLimit"];
    const readonlyViewProperties = ["attached", "layoutWidth", "layoutHeight", "drawableWidth", "drawableHeight",
      "canGoBack", "canGoForward", "drawerOpenStart", "drawerOpenEnd", "pageCount"];
    const nativeViews = new Map();
    const webViewClientCallbacks = ["onPageStarted", "onPageFinished", "onLoadResource", "onPageCommitVisible", "doUpdateVisitedHistory", "onScaleChanged",
      "onReceivedError", "onReceivedHttpError", "onReceivedLoginRequest", "shouldOverrideUrlLoading", "shouldInterceptRequest", "shouldOverrideKeyEvent", "onUnhandledKeyEvent",
      "onFormResubmission", "onReceivedHttpAuthRequest", "onReceivedSslError", "onReceivedClientCertRequest", "onSafeBrowsingHit", "onRenderProcessGone"];
    const webChromeClientCallbacks = ["onProgressChanged", "onReceivedTitle", "onReceivedIcon", "onReceivedTouchIconUrl", "onConsoleMessage",
      "onJsAlert", "onJsConfirm", "onJsPrompt", "onJsBeforeUnload", "onPermissionRequest", "onPermissionRequestCanceled",
      "onGeolocationPermissionsShowPrompt", "onGeolocationPermissionsHidePrompt", "onShowFileChooser", "onCreateWindow", "onCloseWindow", "onRequestFocus", "onShowCustomView", "onHideCustomView", "getVisitedHistory"];
    let nativeContext;
    const nativeCall = (method, args) => __autojs6_call_autojs("ui", method, args, { permissions: ["ui"] });
    function wrapContext(value) {
      const result = { ...value };
      Object.defineProperties(result, {
        inflate: { value: layout => inflate(layout, result) },
        createView: { value: (type, props) => createView(type, props, result) }
      });
      return Object.freeze(result);
    }
    function context() { return nativeContext ||= nativeCall("context", []).then(wrapContext); }
    function viewToken(value) {
      if (!value || typeof value.__autojs6ViewHandle !== "string" || !value.__autojs6ViewHandle) throw new TypeError("An execution-owned native View handle is required");
      return value;
    }
    function wrapView(value) {
      if (value === null) return null;
      viewToken(value);
      if (nativeViews.has(value.__autojs6ViewHandle)) return nativeViews.get(value.__autojs6ViewHandle);
      // A caller may mutate the original structured-cloned token after wrapping it.
      value = Object.freeze({ ...value, id: value.__autojs6ViewHandle,
        ...(value.context ? { context: Object.freeze({ ...value.context }) } : {}) });
      const output = { ...value, context: value.context && wrapContext(value.context) };
      const listeners = new Map(); let closed = false, reading = false;
      const clients = Object.create(null), clientVersions = new Map(), webTasks = new Map(), earlyWebTasks = new Map();
      const clientInstance = Date.now().toString(36) + "-" + Math.random().toString(36).slice(2);
      let clientSerial = 0, startingWebTasks = 0;
      let subscriptionSerial = 0, subscription = null, subscriptionReady = null, legacyEvents = false;
      let writeTail = Promise.resolve(), sequence = 0;
      const writeErrors = [];
      const usable = () => { if (closed) throw new Error("Native View handle is closed"); };
      function write(patch, deferred = false) {
        usable();
        // Capture assignment values now, before a caller mutates an array/object again.
        const payload = __autojs6_ui_validate_json(patch, "viewUpdate", "patch", 0, [], 1024);
        const order = ++sequence;
        const result = writeTail.then(() => { usable(); return nativeCall("viewUpdate", [value, payload]); });
        writeTail = result.then(() => {}, error => { if (deferred) writeErrors.push({ order, error }); });
        return result;
      }
      async function flush() {
        const end = sequence, barrier = writeTail;
        await barrier;
        const errors = writeErrors.filter(item => item.order <= end);
        for (let index = writeErrors.length - 1; index >= 0; index--) if (writeErrors[index].order <= end) writeErrors.splice(index, 1);
        if (errors.length) throw errors[0].error;
      }
      async function read(name) {
        usable(); await flush(); usable();
        return nativeCall("viewGet", name === undefined ? [value] : [value, name]);
      }
      function command(name, args) {
        usable();
        const payload = __autojs6_ui_validate_json(args, "viewCommand", "args", 0, [], 1024);
        const barrier = flush();
        const result = writeTail.then(async () => { await barrier; usable(); return nativeCall("viewCommand", [value, name, payload]); });
        writeTail = result.then(() => {}, () => {});
        return result;
      }
      function directWebCommand(name, args) {
        usable();
        return nativeCall("viewCommand", [value, name, __autojs6_ui_validate_json(args, "viewCommand", "args", 0, [], 1024)]);
      }
      function evaluateJavascript(source, options = {}) { return webTask("beginWebEvaluation", [source, options], true); }
      function evaluateJavascriptAsync(source, options = {}) { return webTask("beginAsyncWebEvaluation", [source, options], true); }
      function subscribeEvents() {
        if (!subscriptionReady) {
          const id = clientInstance + ":" + ++subscriptionSerial;
          subscriptionReady = (legacyEvents ? Promise.resolve() : directWebCommand("subscribeViewEvents", [id]).then(() => { subscription = id; }).catch(error => {
            if (/subscribeViewEvents/.test(String(error.message)) && /unknown|unsupported/i.test(String(error.message))) legacyEvents = true;
            else throw error;
          }));
        }
        return subscriptionReady;
      }
      function configureWebClient(family, handlers, options = {}) {
        usable();
        const allowed = family === "client" ? webViewClientCallbacks : family === "chrome" ? webChromeClientCallbacks : ["onDownloadStart"];
        if (handlers !== null && (!handlers || typeof handlers !== "object" || Array.isArray(handlers))) throw new TypeError("A WebView client must be a callback object or null");
        const callbacks = { ...handlers };
        for (const [name, handler] of Object.entries(callbacks)) {
          if (!allowed.includes(name) || typeof handler !== "function") throw new TypeError("Unsupported WebView callback: " + name);
        }
        const next = { id: clientInstance + "-" + ++clientSerial, callbacks: Object.freeze(callbacks) };
        const config = __autojs6_ui_validate_json({ family, clientId: next.id, callbacks: Object.keys(callbacks), options }, "configureWebClient", "options", 0, []);
        const barrier = flush();
        const result = writeTail.then(async () => {
          await barrier; usable();
          const previous = clients[family]; clients[family] = next; clientVersions.set(next.id, next); poll();
          try { await subscribeEvents(); await nativeCall("viewCommand", [value, "configureWebClient", [config]]); if (previous) clientVersions.delete(previous.id); }
          catch (error) { clients[family] = previous; clientVersions.delete(next.id); throw error; }
        });
        writeTail = result.then(() => {}, () => {});
        return result;
      }
      function hasWebInterest() {
        return startingWebTasks > 0 || webTasks.size > 0 || [...clientVersions.values()].some(client => Object.keys(client.callbacks).length);
      }
      function settleWebTask(event) {
        const task = webTasks.get(event.taskId);
        if (task) { webTasks.delete(event.taskId); if (event.error) task.reject(new Error(event.error)); else task.resolve(event.value); }
        else if (startingWebTasks) earlyWebTasks.set(event.taskId, event);
      }
      async function webTask(name, args, ordered = false, submitted = () => {}) {
        usable(); startingWebTasks++; poll();
        try {
          await subscribeEvents();
          // Only submission occupies the UI command queue; user decisions do not hold a Bun RPC open.
          const task = await (ordered ? command(name, args) : directWebCommand(name, args)); submitted(); usable();
          return await new Promise((resolve, reject) => {
            webTasks.set(task.taskId, { resolve, reject });
            const early = earlyWebTasks.get(task.taskId);
            if (early) { earlyWebTasks.delete(task.taskId); settleWebTask(early); }
          });
        } finally { startingWebTasks--; if (!startingWebTasks) earlyWebTasks.clear(); }
      }
      // chooseFiles subscribes to View events before it submits, so an unscoped cancel waits for submissions in flight.
      const chooserSubmissions = new Set();
      function chooseFiles(options = {}) {
        let submitted;
        const submission = new Promise(resolve => { submitted = () => { chooserSubmissions.delete(submission); resolve(); }; });
        chooserSubmissions.add(submission);
        return webTask("chooseFiles", [options], false, submitted).finally(submitted);
      }
      async function webCallback(event) {
        const client = clientVersions.get(event.clientId);
        if (!client) return;
        const handler = client.callbacks[event.callback]; if (!handler) return;
        try {
          const result = await handler.call(output, Object.freeze(event.data));
          if (["shouldOverrideUrlLoading", "shouldInterceptRequest", "shouldOverrideKeyEvent", "onConsoleMessage", "onRenderProcessGone"].includes(event.callback) && result !== undefined) {
            throw new TypeError(event.callback + " is a notification; synchronous decisions use the client's native options");
          }
          if (event.requestId && !closed) await directWebCommand("replyWebRequest", [event.requestId, result === undefined ? null : result]);
        } catch (error) {
          if (event.requestId && !closed) await directWebCommand("replyWebRequest", [event.requestId, null]).catch(() => {});
          emit({ type: "webCallbackError", callback: event.callback, message: String(error && error.message || error) });
        }
      }
      const emit = event => {
        if (closed) return;
        if (event.type === "webCallback") { webCallback(event); return; }
        if (event.type === "webTaskResult") { settleWebTask(event); return; }
        if (event.type === "close") closed = true;
        for (const callback of [...listeners.get(event.type) || [], ...listeners.get("*") || []]) callback.call(output, Object.freeze(event));
        if (closed) {
          for (const task of webTasks.values()) task.reject(new Error("Native View handle is closed"));
          webTasks.clear(); earlyWebTasks.clear(); clientVersions.clear();
          for (const family of Object.keys(clients)) delete clients[family];
          listeners.clear(); nativeViews.delete(value.__autojs6ViewHandle);
        }
      };
      async function poll() {
        if (reading || closed) return; reading = true;
        try {
          await subscribeEvents();
          while (!closed && (hasWebInterest() || [...listeners.values()].some(list => list.length))) {
            for (const event of await nativeCall("viewEvents", subscription ? [value, subscription] : [value])) emit(event);
          }
        } catch (error) { emit({ type: "error", message: error.message }); emit({ type: "close" }); }
        finally {
          const previous = subscription; subscription = null; subscriptionReady = null; reading = false;
          if (previous) nativeCall("viewCommand", [value, "releaseViewEvents", [previous]]).catch(() => {});
          if (!closed && (hasWebInterest() || [...listeners.values()].some(list => list.length))) poll();
        }
      }
      function on(name, callback) {
        usable(); if (typeof callback !== "function") throw new TypeError("View event listener must be a function");
        listeners.set(name, [...listeners.get(name) || [], callback]); poll();
        return () => off(name, callback);
      }
      function off(name, callback) { listeners.set(name, (listeners.get(name) || []).filter(fn => fn !== callback && fn.listener !== callback)); }
      function once(name, callback) { const listener = event => { off(name, listener); callback.call(output, event); }; listener.listener = callback; return on(name, listener); }
      Object.defineProperties(output, {
        get: { value: read }, set: { value: patch => write(patch) }, flush: { value: flush },
        setWebViewClient: { value: (client, options) => configureWebClient("client", client, options) },
        setWebChromeClient: { value: (client, options) => configureWebClient("chrome", client, options) },
        setDownloadListener: { value: callback => configureWebClient("download", callback === null ? null : { onDownloadStart: callback }) },
        chooseFiles: { value: chooseFiles },
        cancelFileChooser: { value: async () => { await Promise.all(chooserSubmissions); await directWebCommand("cancelFileChooser", []); } },
        findView: { value: name => findView(value, name) },
        dispose: { value: async () => {
          if (closed) return;
          let failure; try { await flush(); } catch (error) { failure = error; }
          await nativeCall("disposeView", [value]); emit({ type: "close" });
          if (failure) throw failure;
        } },
        on: { value: on }, once: { value: once }, off: { value: off }
      });
      const commands = {
        loadUrl: url => command("loadUrl", [url]),
        loadHtml: (html, baseUrl = null) => command("loadHtml", [html, baseUrl]),
        evaluateJavascript,
        evaluateJavascriptAsync,
        stopMediaCapture: () => command("stopMediaCapture", []),
        enableDisplayCapture: options => command("enableDisplayCapture", [options]),
        disableDisplayCapture: id => command("disableDisplayCapture", [id === undefined ? null : id]),
        startBackgroundRecording: (options = {}) => webTask("startBackgroundRecording", [options], true),
        getBackgroundRecording: () => command("getBackgroundRecording", []),
        stopBackgroundRecording: () => webTask("stopBackgroundRecording", [], true),
        cancelBackgroundRecording: id => command("cancelBackgroundRecording", [id === undefined ? null : id]),
        goBack: () => command("goBack", []), goForward: () => command("goForward", []),
        reload: () => command("reload", []), stopLoading: () => command("stopLoading", []),
        openDrawer: (side = "start", animate = true) => command("openDrawer", [side, animate]),
        closeDrawer: (side = "start", animate = true) => command("closeDrawer", [side, animate]),
        setCurrentItem: (index, smoothScroll = true) => command("setCurrentItem", [index, smoothScroll]),
        showOverflowMenu: () => command("showOverflowMenu", []), hideOverflowMenu: () => command("hideOverflowMenu", []),
        exitFullscreen: () => command("exitFullscreen", []),
        download: options => command("download", [options]),
        getDownload: id => command("getDownload", [id]),
        cancelDownload: id => command("cancelDownload", [id]),
        readDownload: async (id, offset = 0, length = 32768) => {
          const result = await command("readDownload", [id, offset, length]);
          return Uint8Array.from(atob(result.base64), value => value.charCodeAt(0));
        },
        readDocument: async (uri, offset = 0, length = 32768) => {
          const result = await command("readDocument", [uri, offset, length]);
          return Uint8Array.from(atob(result.base64), value => value.charCodeAt(0));
        }
      };
      for (const [name, method] of Object.entries(commands)) Object.defineProperty(output, name, { value: method });
      for (const name of writableViewProperties) Object.defineProperty(output, name, {
        get: () => read(name), set: assigned => { write({ [name]: assigned }, true).catch(() => {}); }
      });
      for (const name of readonlyViewProperties) Object.defineProperty(output, name, { get: () => read(name) });
      Object.defineProperty(output, "xmlId", { get: () => read("id") });
      Object.freeze(output); nativeViews.set(value.__autojs6ViewHandle, output); return output;
    }
    async function inflate(layout, scope) {
      const args = [layout]; if (scope !== undefined) args.push(scope);
      return wrapView(await nativeCall("inflate", args));
    }
    function createView(type, props = {}, scope) { return inflate({ ...props, type: String(type).replace(/^android\.widget\./, "") }, scope); }
    async function findView(root, name) { return wrapView(await nativeCall("findView", [typeof root === "string" ? root : viewToken(root), name])); }

    let __autojs6_limited_ui_cache;
    const __autojs6_bridge_signal_from_options = options => options && options.signal;
    const __autojs6_bridge_error = (message, code, module, method) => Object.assign(new Error(message), { code, module, method });
  function __autojs6_ui_options(value) {
    return value && typeof value === "object" && !Array.isArray(value) ? value : {};
  }
  function __autojs6_ui_timeout_from_options(options, fallbackMs) {
    return options && typeof options === "object" && options.timeoutMs !== undefined
      ? options.timeoutMs
      : fallbackMs;
  }
  function __autojs6_ui_bridge_options(options, fallbackMs) {
    return {
      timeoutMs: __autojs6_ui_timeout_from_options(options, fallbackMs),
      permissions: ["ui"],
      signal: __autojs6_bridge_signal_from_options(options)
    };
  }
  function __autojs6_ui_invalid_argument_error(methodName, message) {
    return __autojs6_bridge_error(
      message,
      "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST",
      "ui",
      methodName
    );
  }
  function __autojs6_ui_closed_error(methodName) {
    return __autojs6_bridge_error(
      "AutoJs6 ui." + methodName + " requires an active UI handle.",
      "ERR_AUTOJS6_BRIDGE_INVALID_REQUEST",
      "ui",
      methodName
    );
  }
  function __autojs6_ui_validate_json(value, methodName, path, depth, seen, arrayLimit = 128) {
    if (depth > 16) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor is too deep at " + path + ".");
    }
    if (value === null || typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      if (typeof value === "number" && !Number.isFinite(value)) {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor contains a non-finite number at " + path + ".");
      }
      return value;
    }
    if (!value || typeof value !== "object") {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must be JSON-only at " + path + ".");
    }
    if (seen.indexOf(value) >= 0) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must not contain cycles.");
    }
    seen.push(value);
    if (Array.isArray(value)) {
      if (value.length > arrayLimit) {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor array is too large at " + path + ".");
      }
      const arrayResult = value.map(function(entry, index) {
        return __autojs6_ui_validate_json(entry, methodName, path + "[" + index + "]", depth + 1, seen, arrayLimit);
      });
      seen.pop();
      return arrayResult;
    }
    const prototype = Object.getPrototypeOf(value);
    if (prototype !== Object.prototype && prototype !== null) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must use plain JSON objects at " + path + ".");
    }
    const keys = Object.keys(value);
    if (keys.length > 64) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor object is too large at " + path + ".");
    }
    const objectResult = Object.create(null);
    keys.forEach(function(key) {
      const entry = value[key];
      if (entry === undefined || typeof entry === "function" || typeof entry === "symbol" || typeof entry === "bigint") {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must be JSON-only at " + path + "." + key + ".");
      }
      objectResult[key] = __autojs6_ui_validate_json(entry, methodName, path + "." + key, depth + 1, seen, arrayLimit);
    });
    seen.pop();
    return objectResult;
  }
  function __autojs6_ui_descriptor(value, methodName) {
    const descriptor = __autojs6_ui_validate_json(value, methodName, "layout", 0, []);
    const text = JSON.stringify(descriptor);
    if (!text || text.length > 32768) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor exceeds 32768 characters.");
    }
    if (!descriptor || typeof descriptor !== "object" || Array.isArray(descriptor)) {
      throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " requires a JSON object descriptor.");
    }
    return descriptor;
  }
  const __autojs6_ui_state_type = "autojs6.ui.state";
  const __autojs6_ui_binding_type = "autojs6.ui.binding";
  const __autojs6_ui_bindable_properties = Object.freeze({
    text: true,
    value: true,
    checked: true,
    enabled: true,
    progress: true,
    max: true,
    hint: true,
    contentDescription: true,
    selectedIndex: true
  });
  function __autojs6_ui_json_clone(value, methodName, path) {
    return __autojs6_ui_validate_json(value, methodName, path || "value", 0, []);
  }
  function __autojs6_ui_is_state(value) {
    return !!(value && typeof value === "object" && value.__autojs6UiState === __autojs6_ui_state_type);
  }
  function __autojs6_ui_is_binding(value) {
    return !!(value && typeof value === "object" && value.__autojs6UiBinding === __autojs6_ui_binding_type);
  }
  function __autojs6_ui_state(initialValue) {
    const initial = initialValue === undefined ? {} : __autojs6_ui_json_clone(initialValue, "state", "state");
    if (!initial || typeof initial !== "object" || Array.isArray(initial)) {
      throw __autojs6_ui_invalid_argument_error("state", "ui.state requires a JSON object initial value.");
    }
    const values = JSON.parse(JSON.stringify(initial));
    const listeners = [];
    let api = null;
    function cloneSnapshot() {
      return Object.freeze(JSON.parse(JSON.stringify(values)));
    }
    function notify(keys) {
      const event = Object.freeze({
        keys: Object.freeze(keys.slice()),
        snapshot: cloneSnapshot()
      });
      listeners.slice().forEach(function(listener) {
        listener(event);
      });
    }
    api = {
      get(key) {
        const name = key === undefined || key === null ? "" : String(key);
        if (!Object.prototype.hasOwnProperty.call(values, name)) {
          return undefined;
        }
        return __autojs6_ui_json_clone(values[name], "state", "state." + name);
      },
      set(key, value) {
        const name = key === undefined || key === null ? "" : String(key).trim();
        if (!name) {
          throw __autojs6_ui_invalid_argument_error("state", "ui.state.set requires a non-empty key.");
        }
        values[name] = __autojs6_ui_json_clone(value, "state", "state." + name);
        notify([name]);
        return api;
      },
      update(nextValues) {
        const descriptor = __autojs6_ui_json_clone(nextValues, "state", "state");
        if (!descriptor || typeof descriptor !== "object" || Array.isArray(descriptor)) {
          throw __autojs6_ui_invalid_argument_error("state", "ui.state.update requires a JSON object.");
        }
        const keys = Object.keys(descriptor);
        keys.forEach(function(key) {
          values[key] = descriptor[key];
        });
        if (keys.length > 0) {
          notify(keys);
        }
        return api;
      },
      snapshot: cloneSnapshot,
      subscribe(callback) {
        if (typeof callback !== "function") {
          throw __autojs6_ui_invalid_argument_error("state", "ui.state.subscribe requires a callback.");
        }
        listeners.push(callback);
        return function unsubscribe() {
          const index = listeners.indexOf(callback);
          if (index >= 0) {
            listeners.splice(index, 1);
          }
        };
      }
    };
    Object.defineProperty(api, "__autojs6UiState", {
      value: __autojs6_ui_state_type
    });
    return Object.freeze(api);
  }
  function __autojs6_ui_bind(state, key) {
    if (!__autojs6_ui_is_state(state)) {
      throw __autojs6_ui_invalid_argument_error("bind", "ui.bind requires a ui.state object.");
    }
    const name = key === undefined || key === null ? "" : String(key).trim();
    if (!name) {
      throw __autojs6_ui_invalid_argument_error("bind", "ui.bind requires a non-empty state key.");
    }
    return Object.freeze({
      __autojs6UiBinding: __autojs6_ui_binding_type,
      state,
      key: name
    });
  }
  function __autojs6_ui_prepare_descriptor(value, methodName) {
    const bindings = [];
    function walk(entry, path, componentId, propertyName, depth, seen) {
      if (__autojs6_ui_is_binding(entry)) {
        if (!componentId || !propertyName || !__autojs6_ui_bindable_properties[propertyName]) {
          throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " binding at " + path + " requires a component id and bindable property.");
        }
        bindings.push({
          state: entry.state,
          key: entry.key,
          id: componentId,
          property: propertyName
        });
        return entry.state.get(entry.key);
      }
      if (entry === null || typeof entry === "string" || typeof entry === "number" || typeof entry === "boolean") {
        return entry;
      }
      if (!entry || typeof entry !== "object") {
        return entry;
      }
      if (seen.indexOf(entry) >= 0) {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must not contain cycles.");
      }
      if (depth > 16) {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor is too deep at " + path + ".");
      }
      seen.push(entry);
      if (Array.isArray(entry)) {
        const arrayResult = entry.map(function(child, index) {
          return walk(child, path + "[" + index + "]", componentId, propertyName, depth + 1, seen);
        });
        seen.pop();
        return arrayResult;
      }
      const prototype = Object.getPrototypeOf(entry);
      if (prototype !== Object.prototype && prototype !== null) {
        throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + " descriptor must use plain JSON objects at " + path + ".");
      }
      const currentId = entry.id === undefined || entry.id === null ? componentId : String(entry.id);
      const objectResult = Object.create(null);
      Object.keys(entry).forEach(function(key) {
        objectResult[key] = walk(entry[key], path + "." + key, currentId, key, depth + 1, seen);
      });
      seen.pop();
      return objectResult;
    }
    return {
      descriptor: walk(value, "layout", "", "", 0, []),
      bindings
    };
  }
  function __autojs6_ui_handle_id(value) {
    if (typeof value === "string") {
      return value.trim();
    }
    if (value && typeof value === "object") {
      return String(value.id || value.handleId || value.__autojs6UiHandle || "").trim();
    }
    return "";
  }
  function __autojs6_ui_handle(result, moduleOptions, bindings) {
    const id = __autojs6_ui_handle_id(result);
    if (!id) {
      throw __autojs6_ui_invalid_argument_error("showLayout", "ui.showLayout provider returned an invalid UI handle.");
    }
    const listeners = Object.create(null);
    let closed = false;
    const stopEvents = __autojs6_bridge_attach_events(result, dispatchEvent, false);
    let terminalEventDispatching = false;
    let drainTimer = null;
    let draining = false;
    let drainGeneration = 0;
    let pendingBindingPatches = [];
    let bindingFlushScheduled = false;
    const bindingUnsubscribers = [];
    const defaultDrainIntervalMs = Math.max(10, Math.min(1000, Number(moduleOptions.drainIntervalMs) || 50));
    function listenerCount() {
      return Object.keys(listeners).reduce(function(total, key) {
        return total + listeners[key].length;
      }, 0);
    }
    function stopDrainIfIdle() {
      if (stopEvents) stopEvents.ref(listenerCount() > 0);
      if (listenerCount() === 0 && drainTimer !== null) {
        clearInterval(drainTimer);
        drainTimer = null;
      }
    }
    function terminate() {
      if (closed) {
        return false;
      }
      closed = true;
      if (stopEvents) stopEvents();
      terminalEventDispatching = false;
      drainGeneration += 1;
      draining = false;
      if (drainTimer !== null) {
        clearInterval(drainTimer);
        drainTimer = null;
      }
      bindingUnsubscribers.splice(0).forEach(function(unsubscribe) {
        try {
          unsubscribe();
        } catch (error) {
          setTimeout(function() { throw error; }, 0);
        }
      });
      bindingFlushScheduled = false;
      pendingBindingPatches = [];
      Object.keys(listeners).forEach(function(eventName) {
        delete listeners[eventName];
      });
      return true;
    }
    function dispatchEvent(event) {
      if (closed || !event || typeof event !== "object") {
        return false;
      }
      const type = event.type === undefined || event.type === null ? "" : String(event.type);
      const terminal = type === "close";
      if (terminal) {
        terminalEventDispatching = true;
      }
      const targets = []
        .concat(listeners[type] || [])
        .concat(listeners["*"] || []);
      targets.forEach(function(callback) {
        try {
          callback(Object.freeze(Object.assign({}, event, { handleId: id })));
        } catch (error) {
          setTimeout(function() { throw error; }, 0);
        }
      });
      if (terminal) {
        terminate();
      }
      return terminal;
    }
    function drain(options) {
      if (closed || terminalEventDispatching || draining || listenerCount() === 0) {
        return;
      }
      draining = true;
      const generation = drainGeneration;
      const bridgeOptions = __autojs6_ui_options(options || moduleOptions);
      __autojs6_call_autojs(
        "ui",
        "drainEvents",
        [id, { maxEvents: 16 }],
        __autojs6_ui_bridge_options(bridgeOptions, 5000)
      ).then(function(events) {
        if (closed || generation !== drainGeneration) {
          return;
        }
        if (Array.isArray(events)) {
          for (const event of events) {
            if (dispatchEvent(event)) {
              break;
            }
          }
        }
      }, function() {
        return undefined;
      }).finally(function() {
        if (generation === drainGeneration) {
          draining = false;
        }
      });
    }
    function ensureDrain(options) {
      if (stopEvents) { stopEvents.ref(listenerCount() > 0); return; }
      if (closed || terminalEventDispatching || drainTimer !== null || listenerCount() === 0) {
        return;
      }
      const intervalMs = Math.max(10, Math.min(1000, Number((options && options.drainIntervalMs) || moduleOptions.drainIntervalMs) || defaultDrainIntervalMs));
      drainTimer = setInterval(function() { drain(options); }, intervalMs);
      drain(options);
    }
    function on(eventName, callback, options) {
      if (closed || terminalEventDispatching) {
        throw __autojs6_ui_closed_error("on");
      }
      const event = eventName === undefined || eventName === null ? "" : String(eventName).trim();
      if (!event || typeof callback !== "function") {
        throw __autojs6_ui_invalid_argument_error("on", "ui.handle.on requires an event name and callback.");
      }
      if (!listeners[event]) {
        listeners[event] = [];
      }
      listeners[event].push(callback);
      ensureDrain(options);
      return function unsubscribe() {
        const bucket = listeners[event] || [];
        const index = bucket.indexOf(callback);
        if (index >= 0) {
          bucket.splice(index, 1);
        }
        stopDrainIfIdle();
      };
    }
    function once(event, callback, options) {
      const wrapped = value => { off(event, wrapped); callback(value); };
      wrapped.listener = callback;
      return on(event, wrapped, options);
    }
    function off(event, callback) {
      const bucket = listeners[event] || [];
      listeners[event] = bucket.filter(listener => listener !== callback && listener.listener !== callback);
      stopDrainIfIdle();
    }
    function update(patch, options) {
      if (closed || terminalEventDispatching) {
        return Promise.reject(__autojs6_ui_closed_error("update"));
      }
      let descriptor;
      try {
        descriptor = __autojs6_ui_descriptor(patch, "update");
      } catch (error) {
        return Promise.reject(error);
      }
      const bridgeOptions = __autojs6_ui_options(options || moduleOptions);
      return __autojs6_call_autojs(
        "ui",
        "update",
        [id, descriptor],
        __autojs6_ui_bridge_options(bridgeOptions, 10000)
      ).then(function() {
        return undefined;
      });
    }
    function batchUpdate(patches, options) {
      if (closed || terminalEventDispatching) {
        return Promise.reject(__autojs6_ui_closed_error("batchUpdate"));
      }
      if (!Array.isArray(patches)) {
        return Promise.reject(__autojs6_ui_invalid_argument_error("batchUpdate", "ui.handle.batchUpdate requires an array of patches."));
      }
      if (patches.length === 0) {
        return Promise.resolve(undefined);
      }
      if (patches.length > 64) {
        return Promise.reject(__autojs6_ui_invalid_argument_error("batchUpdate", "ui.handle.batchUpdate patch array exceeds 64 entries."));
      }
      let descriptors;
      try {
        descriptors = patches.map(function(patch) {
          return __autojs6_ui_descriptor(patch, "batchUpdate");
        });
      } catch (error) {
        return Promise.reject(error);
      }
      const bridgeOptions = __autojs6_ui_options(options || moduleOptions);
      return __autojs6_call_autojs(
        "ui",
        "batchUpdate",
        [id, descriptors],
        __autojs6_ui_bridge_options(bridgeOptions, 10000)
      ).then(function() {
        return undefined;
      });
    }
    function flushBindingPatches() {
      bindingFlushScheduled = false;
      if (closed || terminalEventDispatching || pendingBindingPatches.length === 0) {
        pendingBindingPatches = [];
        return;
      }
      const patches = pendingBindingPatches.slice();
      pendingBindingPatches = [];
      batchUpdate(patches, moduleOptions).catch(function() {
        return undefined;
      });
    }
    function enqueueBindingPatch(patch) {
      if (closed || terminalEventDispatching) {
        return;
      }
      pendingBindingPatches.push(patch);
      if (!bindingFlushScheduled) {
        bindingFlushScheduled = true;
        Promise.resolve().then(flushBindingPatches);
      }
    }
    (Array.isArray(bindings) ? bindings : []).forEach(function(binding) {
      const off = binding.state.subscribe(function(event) {
        if (!event || !Array.isArray(event.keys) || event.keys.indexOf(binding.key) < 0) {
          return;
        }
        const patch = Object.create(null);
        patch.id = binding.id;
        patch[binding.property] = binding.state.get(binding.key);
        enqueueBindingPatch(patch);
      });
      bindingUnsubscribers.push(off);
    });
    function close(options) {
      if (closed || terminalEventDispatching) {
        return Promise.resolve(undefined);
      }
      const bridgeOptions = __autojs6_ui_options(options || moduleOptions);
      terminate();
      return __autojs6_call_autojs(
        "ui",
        "close",
        [id],
        __autojs6_ui_bridge_options(bridgeOptions, 10000)
      ).then(function() {
        return undefined;
      });
    }
    return Object.freeze({
      id,
      ...(result.rootView ? { rootView: wrapView(result.rootView), findView: name => findView(id, name) } : {}),
      get closed() { return closed; },
      subscriptionId: result.subscriptionId,
      on,
      once,
      off,
      update,
      batchUpdate,
      close
    });
  }
    // api-coverage P3.3 (appendix C Q164 (a)): Rhino's Activity content view and window members. The execution has one
    // content window (the host keeps it across threads: showLayout with contentView replaces its root in the same
    // Activity); this thread remembers the root and the XML ids of the layouts it set, so root / findById /
    // findByStringId answer synchronously. Custom widgets (registerWidget / Widget) and {{expressions}} (bindingContext)
    // are expanded here before the XML goes to the host; widget hooks and attribute setters receive native View handles.
    function __autojs6_ui_emitter() {
      const events = new Map(); let maxListeners = 10; const hooks = new Set();
      const list = name => events.get(name) || [];
      const changed = name => hooks.forEach(hook => { try { hook(name); } catch (_) {} });
      const emitter = {
        on(name, listener) { return emitter.addListener(name, listener); },
        addListener(name, listener) {
          if (typeof listener !== "function") throw new TypeError("The listener must be a function");
          events.set(name, list(name).concat({ listener, once: false })); changed(name); return emitter;
        },
        prependListener(name, listener) {
          if (typeof listener !== "function") throw new TypeError("The listener must be a function");
          events.set(name, [{ listener, once: false }].concat(list(name))); changed(name); return emitter;
        },
        once(name, listener) {
          if (typeof listener !== "function") throw new TypeError("The listener must be a function");
          events.set(name, list(name).concat({ listener, once: true })); changed(name); return emitter;
        },
        prependOnceListener(name, listener) {
          if (typeof listener !== "function") throw new TypeError("The listener must be a function");
          events.set(name, [{ listener, once: true }].concat(list(name))); changed(name); return emitter;
        },
        off(name, listener) { return emitter.removeListener(name, listener); },
        removeListener(name, listener) {
          const entries = list(name), index = entries.findIndex(entry => entry.listener === listener);
          if (index >= 0) { events.set(name, entries.slice(0, index).concat(entries.slice(index + 1))); changed(name); }
          return emitter;
        },
        removeAllListeners(name) {
          if (name === undefined) { const names = [...events.keys()]; events.clear(); names.forEach(changed); }
          else { events.delete(name); changed(name); }
          return emitter;
        },
        emit(name, ...args) {
          const entries = list(name);
          if (entries.length === 0) {
            if (name === "error") throw args[0] instanceof Error ? args[0] : new Error("Unhandled error event");
            return false;
          }
          for (const entry of entries) {
            if (entry.once) emitter.removeListener(name, entry.listener);
            entry.listener.apply(emitter, args);
          }
          return true;
        },
        listeners(name) { return list(name).map(entry => entry.listener); },
        rawListeners(name) { return emitter.listeners(name); },
        listenerCount(name) { return list(name).length; },
        eventNames() { return [...events.keys()].filter(name => list(name).length > 0); },
        setMaxListeners(value) { maxListeners = Number(value); return emitter; },
        getMaxListeners() { return maxListeners; }
      };
      Object.defineProperty(emitter, "__autojs6Watch", { value: hook => { hooks.add(hook); return () => hooks.delete(hook); } });
      return emitter;
    }
    // A small XML reader for layouts: elements, attributes, text, comments and the XML declaration; values stay raw.
    function __autojs6_ui_parse_xml(source, methodName) {
      const text = String(source), root = { tag: "#root", attrs: [], children: [] }, stack = [root];
      let index = 0;
      const fail = message => { throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + ": " + message); };
      while (index < text.length) {
        if (text.startsWith("<!--", index)) { const end = text.indexOf("-->", index + 4); if (end < 0) fail("unterminated comment"); index = end + 3; continue; }
        if (text.startsWith("<?", index)) { const end = text.indexOf("?>", index + 2); if (end < 0) fail("unterminated declaration"); index = end + 2; continue; }
        if (text.startsWith("<![CDATA[", index)) {
          const end = text.indexOf("]]>", index); if (end < 0) fail("unterminated CDATA");
          stack[stack.length - 1].children.push({ text: text.slice(index, end + 3) }); index = end + 3; continue;
        }
        if (text.startsWith("</", index)) {
          const end = text.indexOf(">", index); if (end < 0) fail("unterminated end tag");
          const name = text.slice(index + 2, end).trim(), open = stack.pop();
          if (!open || open.tag !== name) fail("mismatched end tag </" + name + ">");
          index = end + 1; continue;
        }
        if (text[index] === "<") {
          const match = /^<([A-Za-z_][\w.:-]*)/.exec(text.slice(index, index + 256)); if (!match) fail("invalid tag at " + index);
          const element = { tag: match[1], attrs: [], children: [] }; index += match[0].length;
          for (;;) {
            const rest = /^\s*/.exec(text.slice(index))[0]; index += rest.length;
            if (text.startsWith("/>", index)) { index += 2; stack[stack.length - 1].children.push(element); break; }
            if (text[index] === ">") { index += 1; stack[stack.length - 1].children.push(element); stack.push(element); break; }
            const attr = /^([A-Za-z_][\w.:-]*)\s*=\s*("([^"]*)"|'([^']*)')/.exec(text.slice(index, index + 65536));
            if (!attr) fail("invalid attribute in <" + element.tag + ">");
            element.attrs.push([attr[1], attr[3] !== undefined ? attr[3] : attr[4]]); index += attr[0].length;
          }
          if (stack.length > 33) fail("layout is nested too deeply");
          continue;
        }
        const next = text.indexOf("<", index), end = next < 0 ? text.length : next;
        const chunk = text.slice(index, end);
        if (chunk.trim()) { if (stack.length === 1) fail("text outside the root element"); stack[stack.length - 1].children.push({ text: chunk }); }
        index = end;
      }
      if (stack.length !== 1) fail("unterminated element <" + stack[stack.length - 1].tag + ">");
      const elements = root.children.filter(child => child.tag);
      if (elements.length !== 1) fail("a layout needs exactly one root element");
      return elements[0];
    }
    function __autojs6_ui_xml_text(node) {
      if (node.text !== undefined) return node.text;
      const attrs = node.attrs.map(([name, value]) => " " + name + "=\"" + String(value).replace(/"/g, "&quot;") + "\"").join("");
      return node.children.length === 0 ? "<" + node.tag + attrs + "/>"
        : "<" + node.tag + attrs + ">" + node.children.map(__autojs6_ui_xml_text).join("") + "</" + node.tag + ">";
    }
    const __autojs6_ui_xml_escape = value => String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;");
    const __autojs6_ui_widgets = new Map();
    let __autojs6_ui_binding_context = globalThis;
    let __autojs6_ui_android_layout = true;
    let __autojs6_ui_widget_serial = 0;
    // Rhino's ui.Widget: render() returns the widget's XML, defineAttr(name, alias? | getter, setter? | applier) declares
    // attributes whose setters / appliers run with the created View (a native View handle here).
    function Widget() {
      if (!(this instanceof Widget)) return new Widget();
      Object.defineProperty(this, "__attrs__", { value: Object.create(null), writable: true, configurable: true });
    }
    Widget.prototype.render = function() { return "< />"; };
    Widget.prototype.defineAttr = function(attrName, aliasOrGetter, setterOrApplier) {
      if (attrName === undefined || attrName === null) throw new TypeError("Widget.defineAttr requires an attribute name");
      const name = String(attrName), self = this;
      let alias = name, applier = null, getter, setter;
      if (typeof aliasOrGetter === "string") { alias = aliasOrGetter; applier = typeof setterOrApplier === "function" ? setterOrApplier : null; }
      else if (typeof aliasOrGetter === "function" && typeof setterOrApplier !== "function") applier = aliasOrGetter;
      if (typeof aliasOrGetter === "function" && typeof setterOrApplier === "function") { getter = aliasOrGetter; setter = setterOrApplier; }
      else {
        getter = () => self[alias];
        setter = function(view, attr, value, defineSetter) {
          self[alias] = value;
          if (applier) return applier.call(self, view, attr, value, defineSetter);
          return undefined;
        };
      }
      if (!this.__attrs__) Widget.call(this);
      this.__attrs__[name] = { getter, setter };
    };
    Widget.prototype.hasAttr = function(attrName) { return !!(this.__attrs__ && this.__attrs__[String(attrName)]); };
    Widget.prototype.getAttr = function(view, attrName, ...args) {
      const attr = this.__attrs__ && this.__attrs__[String(attrName)];
      return attr ? attr.getter.call(this, view, String(attrName), ...args) : undefined;
    };
    Widget.prototype.setAttr = function(view, attrName, value) {
      const attr = this.__attrs__ && this.__attrs__[String(attrName)];
      return attr ? attr.setter.call(this, view, String(attrName), value, () => {}) : undefined;
    };
    function registerWidget(name, widget) {
      const niceName = typeof name === "string" ? name : name === undefined || name === null ? "" : String(name);
      if (!niceName) throw new RangeError("ui.registerWidget: name must be a non-empty string");
      if (typeof widget !== "function") throw new TypeError("ui.registerWidget: widget must be a function");
      __autojs6_ui_widgets.set(niceName, widget);
    }
    function __autojs6_ui_evaluate(expression) {
      // eslint-disable-next-line no-new-func
      return new Function("__autojs6_scope", "with (__autojs6_scope) { return (" + expression + "); }")(__autojs6_ui_binding_context);
    }
    function __autojs6_ui_bind_value(value) {
      const whole = /^\s*\{\{([\s\S]+?)\}\}\s*$/.exec(value);
      if (whole) { const result = __autojs6_ui_evaluate(whole[1]); return result === undefined || result === null ? "" : String(result); }
      return value.replace(/\{\{([\s\S]+?)\}\}/g, (_, expression) => { const result = __autojs6_ui_evaluate(expression); return result === undefined || result === null ? "" : String(result); });
    }
    // Expands widgets and {{expressions}} and lists the XML ids with their ancestors (for findByStringId).
    function __autojs6_ui_prepare_xml(source, methodName) {
      const hooks = [], ids = new Map();
      function expand(node, depth) {
        if (node.text !== undefined) return node;
        if (depth > 8) throw __autojs6_ui_invalid_argument_error(methodName, "ui." + methodName + ": widgets are nested too deeply");
        node.attrs = node.attrs.map(([name, value]) => [name, value.indexOf("{{") >= 0 ? __autojs6_ui_bind_value(value) : value]);
        const Ctor = __autojs6_ui_widgets.get(node.tag);
        if (!Ctor) { node.children = node.children.map(child => expand(child, depth)); return node; }
        const instance = new Ctor();
        const markup = typeof instance.render === "function" ? instance.render() : undefined;
        if (markup === undefined || markup === null) throw new TypeError("Function render must return a non-null value");
        const rendered = __autojs6_ui_parse_xml(String(markup), methodName), defined = [];
        for (const [name, value] of node.attrs) {
          const bare = name.replace(/^[\w-]+:/, "");
          if (instance.__attrs__ && instance.__attrs__[bare]) defined.push([bare, value]);
          else rendered.attrs = rendered.attrs.filter(([other]) => other !== name).concat([[name, value]]);
        }
        let id = (rendered.attrs.find(([name]) => name.replace(/^[\w-]+:/, "") === "id") || [])[1];
        if (!id) { id = "__autojs6_widget_" + (++__autojs6_ui_widget_serial); rendered.attrs.push(["id", id]); }
        rendered.children = rendered.children.concat(node.children);
        hooks.push({ instance, id: String(id).replace(/^@\+?id\//, ""), attrs: defined });
        return expand(rendered, depth + 1);
      }
      function collect(node, ancestors) {
        if (node.text !== undefined) return;
        const id = (node.attrs.find(([name]) => name.replace(/^[\w-]+:/, "") === "id") || [])[1];
        const own = id ? String(id).replace(/^@\+?id\//, "") : null;
        if (own && !ids.has(own)) ids.set(own, ancestors);
        node.children.forEach(child => collect(child, own ? ancestors.concat(own) : ancestors));
      }
      const tree = expand(__autojs6_ui_parse_xml(source, methodName), 0);
      collect(tree, []);
      return { xml: __autojs6_ui_xml_text(tree), hooks, ids };
    }
    async function __autojs6_ui_run_hooks(hooks, find) {
      for (const hook of hooks) {
        const view = await find(hook.id);
        if (typeof hook.instance.onViewCreated === "function") await hook.instance.onViewCreated(view);
        for (const [name, value] of hook.attrs) await hook.instance.__attrs__[name].setter.call(hook.instance, view, name, value, () => {});
      }
      for (const hook of hooks) if (typeof hook.instance.onFinishInflation === "function") await hook.instance.onFinishInflation(await find(hook.id));
    }
    function __autojs6_ui_layout_source(value, methodName) {
      if (typeof value === "string") return value;
      if (value && typeof value.toXMLString === "function") return String(value.toXMLString());
      throw new TypeError("ui." + methodName + " requires an XML string");
    }
    const __autojs6_ui_emitter_value = __autojs6_ui_emitter();
    const __autojs6_ui_window_options = new Map();
    let __autojs6_ui_content = null;
    let __autojs6_ui_content_tail = Promise.resolve();
    const __autojs6_ui_wants_back = () => __autojs6_ui_emitter_value.listenerCount("back_pressed") > 0;
    function __autojs6_ui_window_control(op, value) {
      const content = __autojs6_ui_content;
      return content ? nativeCall("windowControl", [content.id, op, value]).then(() => undefined) : Promise.resolve(undefined);
    }
    __autojs6_ui_emitter_value.__autojs6Watch(name => {
      if (name !== "back_pressed" || !__autojs6_ui_content) return;
      const wanted = __autojs6_ui_wants_back();
      if (__autojs6_ui_content.interceptBack !== wanted) {
        __autojs6_ui_content.interceptBack = wanted;
        __autojs6_ui_window_control("interceptBack", wanted).catch(() => {});
      }
    });
    function __autojs6_ui_lifecycle(event) {
      const name = String(event && event.name || "");
      if (!name) return;
      if (name === "back_pressed") {
        const value = { consumed: false };
        try { __autojs6_ui_emitter_value.emit("back_pressed", value); }
        finally { if (!value.consumed) finish().catch(() => {}); }
        return;
      }
      __autojs6_ui_emitter_value.emit(name, event);
    }
    async function __autojs6_ui_set_content(layout, prepared) {
      const run = __autojs6_ui_content_tail.then(async () => {
        // A replaced root keeps the window and its event subscription.
        const live = __autojs6_ui_content && !__autojs6_ui_content.closed;
        const result = await __autojs6_call_autojs("ui", "showLayout", [layout, { contentView: true }], { permissions: ["ui"], ...(live ? { events: false } : {}), timeoutMs: 10000 });
        const rootView = wrapView(result.rootView);
        let content = __autojs6_ui_content;
        if (!content || content.id !== result.id || content.closed) {
          const handle = __autojs6_ui_handle(result, {}, []);
          content = { id: result.id, handle, closed: false, interceptBack: false };
          handle.on("lifecycle", __autojs6_ui_lifecycle);
          handle.on("close", () => { content.closed = true; if (__autojs6_ui_content === content) __autojs6_ui_content = null; });
          __autojs6_ui_content = content;
        }
        content.root = rootView;
        content.views = new Map(); content.ancestors = prepared ? prepared.ids : new Map();
        const ids = [...content.ancestors.keys()].slice(0, 512);
        const found = await Promise.all(ids.map(id => findView(result.id, id).catch(() => null)));
        ids.forEach((id, index) => { if (found[index]) content.views.set(id, found[index]); });
        content.interceptBack = __autojs6_ui_wants_back();
        if (content.interceptBack) await nativeCall("windowControl", [content.id, "interceptBack", true]);
        for (const [op, value] of __autojs6_ui_window_options) await nativeCall("windowControl", [content.id, op, value]);
        if (prepared) await __autojs6_ui_run_hooks(prepared.hooks, async id => content.views.get(id) || await findView(result.id, id));
      });
      __autojs6_ui_content_tail = run.catch(() => {});
      return run;
    }
    function layout(xml) {
      let prepared;
      try { prepared = __autojs6_ui_prepare_xml(__autojs6_ui_layout_source(xml, "layout"), "layout"); }
      catch (error) { return Promise.reject(error); }
      return __autojs6_ui_set_content(prepared.xml, prepared);
    }
    function layoutFile(path) {
      if (typeof path !== "string" || !path) return Promise.reject(new TypeError("ui.layoutFile requires a file path"));
      let source;
      try {
        const fs = typeof process === "object" && process && typeof process.getBuiltinModule === "function" ? process.getBuiltinModule("fs") : null;
        if (!fs) throw new Error("ui.layoutFile needs the runtime's file system module");
        source = fs.readFileSync(path, "utf8");
      } catch (error) { return Promise.reject(error); }
      return layout(source);
    }
    function setContentView(view) {
      try { viewToken(view); } catch (error) { return Promise.reject(error); }
      return __autojs6_ui_set_content(view, null);
    }
    function finish() {
      const content = __autojs6_ui_content;
      if (!content || content.closed) return Promise.resolve(undefined);
      content.closed = true; __autojs6_ui_content = null;
      return content.handle.close();
    }
    function findById(id) {
      const content = __autojs6_ui_content;
      if (id === undefined || id === null || !content) return null;
      return content.views.get(String(id).replace(/^@\+?id\//, "")) || null;
    }
    function findByStringId(view, id) {
      const content = __autojs6_ui_content;
      if (!view) throw new TypeError("ui.findByStringId requires a View");
      if (!content || id === undefined || id === null) return null;
      const target = String(id).replace(/^@\+?id\//, ""), found = content.views.get(target) || null;
      if (!found || view === content.root || view === found) return found;
      const owner = [...content.views].find(([, handle]) => handle === view);
      return owner && (content.ancestors.get(target) || []).includes(owner[0]) ? found : null;
    }
    function run(action) {
      if (typeof action !== "function") throw new TypeError("ui.run requires a function");
      try { return Promise.resolve(action()); } catch (error) { return Promise.reject(error); }
    }
    function post(action, delay) {
      if (typeof action !== "function") throw new TypeError("ui.post requires a function");
      if (delay !== undefined && delay !== null && !(Number.isFinite(Number(delay)) && Number(delay) >= 0)) throw new RangeError("ui.post delay must be a non-negative number");
      setTimeout(action, delay === undefined || delay === null ? 0 : Number(delay));
      return true;
    }
    function useAndroidLayout(enabled) {
      __autojs6_ui_android_layout = enabled === null ? null : enabled === undefined ? true : (() => {
        if (typeof enabled !== "boolean") throw new TypeError("ui.useAndroidLayout requires a boolean");
        return enabled;
      })();
    }
    function __autojs6_ui_remember(op, value) {
      __autojs6_ui_window_options.set(op, value);
      return __autojs6_ui_window_control(op, value);
    }
    const __autojs6_ui_color = (method, value) => {
      if (typeof value !== "string" && typeof value !== "number") throw new TypeError("ui." + method + " requires a color string or number");
      return value;
    };
    function __autojs6_ui_bar_options(method, options) {
      if (options === undefined || options === null) return {};
      if (typeof options !== "object" || Array.isArray(options)) throw new TypeError("ui." + method + " options must be an object");
      const output = {};
      for (const key of ["withComputed", "withDimen", "ignoreVisibility"]) if (options[key] !== undefined) output[key] = Boolean(options[key]);
      return output;
    }
    const __autojs6_ui_bar = (method, kind, visible) => options => {
      try { return nativeCall("barHeight", [kind, visible, __autojs6_ui_bar_options(method, options)]); }
      catch (error) { return Promise.reject(error); }
    };
    const __autojs6_ui_promise = fn => (...args) => { try { return fn(...args); } catch (error) { return Promise.reject(error); } };
    const __autojs6_ui_resources = Object.freeze(Object.fromEntries(["anim", "animator", "array", "attr", "bool", "color", "dimen", "drawable", "font",
      "fraction", "id", "integer", "interpolator", "layout", "menu", "mipmap", "plurals", "raw", "string", "style", "styleable", "transition", "xml"]
      .map(type => [type, new Proxy(Object.freeze({}), {
        get: (_, name) => typeof name === "symbol" ? undefined : "@" + type + "/" + name,
        has: () => true
      })])));
    const __autojs6_ui_rhino_members = {
      layout, layoutFile, setContentView, finish, findById, findByStringId, run, post, registerWidget, useAndroidLayout,
      isUiThread: () => false,
      keepScreenOn: __autojs6_ui_promise(() => __autojs6_ui_remember("keepScreenOn", true)),
      backgroundColor: __autojs6_ui_promise(color => __autojs6_ui_remember("backgroundColor", __autojs6_ui_color("backgroundColor", color))),
      statusBarColor: __autojs6_ui_promise(color => __autojs6_ui_remember("statusBarColor", __autojs6_ui_color("statusBarColor", color))),
      statusBarIconLight: __autojs6_ui_promise(isLight => __autojs6_ui_remember("statusBarIconLight", isLight === undefined || isLight === null ? true : Boolean(isLight))),
      statusBarIconLightBy: __autojs6_ui_promise(color => __autojs6_ui_remember("statusBarIconLightBy", __autojs6_ui_color("statusBarIconLightBy", color))),
      navigationBarColor: __autojs6_ui_promise(color => __autojs6_ui_remember("navigationBarColor", __autojs6_ui_color("navigationBarColor", color))),
      navigationBarIconLight: __autojs6_ui_promise(isLight => __autojs6_ui_remember("navigationBarIconLight", isLight === undefined || isLight === null ? true : Boolean(isLight))),
      navigationBarIconLightBy: __autojs6_ui_promise(color => __autojs6_ui_remember("navigationBarIconLightBy", __autojs6_ui_color("navigationBarIconLightBy", color))),
      getStatusBarHeight: __autojs6_ui_bar("getStatusBarHeight", "status", false),
      getVisibleStatusBarHeight: __autojs6_ui_bar("getVisibleStatusBarHeight", "status", true),
      getNavigationBarHeight: __autojs6_ui_bar("getNavigationBarHeight", "navigation", false),
      getVisibleNavigationBarHeight: __autojs6_ui_bar("getVisibleNavigationBarHeight", "navigation", true)
    };
    // Lazy exports (ESM named bindings skip them): the root and the bar heights change, a binding would freeze the first value.
    const __autojs6_ui_lazy = get => Object.assign(get, { __autojs6LazyExport: true });
    const __autojs6_ui_rhino_getters = {
      root: { get: __autojs6_ui_lazy(() => __autojs6_ui_content ? __autojs6_ui_content.root || null : null), enumerable: true },
      emitter: { get: () => __autojs6_ui_emitter_value, enumerable: true },
      R: { get: () => __autojs6_ui_resources, enumerable: true },
      Widget: { value: Widget, enumerable: true },
      bindingContext: { get: __autojs6_ui_lazy(() => __autojs6_ui_binding_context), set: value => { __autojs6_ui_binding_context = value === undefined || value === null ? globalThis : Object(value); }, enumerable: true },
      statusBarHeight: { get: __autojs6_ui_lazy(() => __autojs6_ui_rhino_members.getStatusBarHeight()), enumerable: true },
      visibleStatusBarHeight: { get: __autojs6_ui_lazy(() => __autojs6_ui_rhino_members.getVisibleStatusBarHeight()), enumerable: true },
      navigationBarHeight: { get: __autojs6_ui_lazy(() => __autojs6_ui_rhino_members.getNavigationBarHeight()), enumerable: true },
      visibleNavigationBarHeight: { get: __autojs6_ui_lazy(() => __autojs6_ui_rhino_members.getVisibleNavigationBarHeight()), enumerable: true }
    };
    // api-coverage P3.3 (appendix C Q165 - Q168): Rhino's floaty, web and terminal modules and console.build, shared by the
    // Node and Bun facades. `deps.call` reaches any host module (default: this facade's call); `deps.exit` ends the
    // execution (exitOnClose); `deps.connectWebSocket(url, options)` opens the runtime's controlled WebSocket (a connection
    // with on / send / close), without it the host websocket routes are polled here.
    function __autojs6_ui_family(deps = {}) {
      const call = deps.call || __autojs6_family_call;
      const exit = () => { if (typeof deps.exit === "function") deps.exit(); };
      const options = (method, value) => {
        if (value === undefined || value === null) return {};
        if (typeof value !== "object" || Array.isArray(value)) throw new TypeError(method + " options must be an object");
        return value;
      };
      const finite = (method, value) => {
        const number = Number(value);
        if (!Number.isFinite(number)) throw new TypeError(method + " requires finite numbers");
        return Math.round(number);
      };
      const settle = fn => (...args) => { try { return Promise.resolve(fn(...args)); } catch (error) { return Promise.reject(error); } };
      const done = () => undefined;

      // floaty: window (resizable frame) and rawWindow; XML ids resolve to native View handles before the Promise settles.
      const floatyWindows = new Set();
      function floatyWindow(handle, raw, rootView, views) {
        let closed = false, exitOnClose = false;
        const control = (op, ...args) => closed || handle.closed
          ? Promise.resolve(op === "geometry" ? { x: -1, y: -1, width: 0, height: 0 } : op === "isAdjustEnabled" ? false : null)
          : call("ui", "floatyControl", [handle.id, op, ...args], { permissions: op === "setTouchable" ? ["ui", "ui.overlay", "floaty.touch"] : ["ui", "ui.overlay"] });
        const geometry = key => control("geometry").then(value => Number(value && value[key]));
        const markClosed = () => { if (closed) return false; closed = true; floatyWindows.delete(window); return true; };
        const window = {
          id: handle.id, rootView,
          findView: id => id === undefined || id === null ? null : views.get(String(id).replace(/^@\+?id\//, "")) || null,
          setPosition: settle((x, y) => control("setPosition", finite("setPosition", x), finite("setPosition", y)).then(done)),
          setSize: settle((width, height) => control("setSize", finite("setSize", width), finite("setSize", height)).then(done)),
          requestFocus: () => control("requestFocus").then(done),
          disableFocus: () => control("disableFocus").then(done),
          exitOnClose() { exitOnClose = true; },
          close() {
            if (!markClosed()) return Promise.resolve(undefined);
            return handle.close().then(() => { if (exitOnClose) exit(); });
          }
        };
        if (raw) window.setTouchable = touchable => control("setTouchable", touchable === undefined ? true : Boolean(touchable)).then(done);
        else {
          window.isAdjustEnabled = () => control("isAdjustEnabled").then(Boolean);
          window.setAdjustEnabled = enabled => control("setAdjustEnabled", enabled === undefined ? true : Boolean(enabled)).then(done);
        }
        for (const key of ["x", "y", "width", "height"]) Object.defineProperty(window, key, { get: () => geometry(key), enumerable: true });
        Object.defineProperty(window, "closed", { get: () => closed || handle.closed, enumerable: true });
        // Rhino reads the views of the layout as window properties (`w.text` for id="text").
        for (const [id, view] of views) if (!(id in window)) Object.defineProperty(window, id, { value: view, enumerable: true });
        handle.on("close", () => { if (markClosed() && exitOnClose) exit(); });
        floatyWindows.add(window);
        return Object.freeze(window);
      }
      function openFloaty(method, raw) {
        const label = "floaty." + method;
        return function(layout, windowOptions) {
          let prepared = null, source, opts;
          try {
            opts = options(label, windowOptions);
            if (layout && typeof layout === "object" && typeof layout.__autojs6ViewHandle === "string") source = viewToken(layout);
            else { prepared = __autojs6_ui_prepare_xml(__autojs6_ui_layout_source(layout, label), label); source = prepared.xml; }
          } catch (error) { return Promise.reject(error); }
          const permissions = raw ? ["ui", "ui.overlay", "floaty.raw"] : ["ui", "ui.overlay"];
          return call("ui", raw ? "floatyRawWindow" : "floatyWindow", [source, opts], { permissions, timeoutMs: 10000 }).then(async result => {
            const handle = __autojs6_ui_handle(result, {}, []), rootView = result.rootView ? wrapView(result.rootView) : null, views = new Map();
            if (prepared && rootView) {
              const ids = [...prepared.ids.keys()].slice(0, 512);
              const found = await Promise.all(ids.map(id => findView(rootView, id).catch(() => null)));
              ids.forEach((id, index) => { if (found[index]) views.set(id, found[index]); });
              await __autojs6_ui_run_hooks(prepared.hooks, async id => views.get(id) || await findView(rootView, id));
            }
            return floatyWindow(handle, raw, rootView, views);
          });
        };
      }
      const overlayPermission = () => call("ui.overlay", "hasPermission", [], { permissions: ["ui.overlay.permission"], timeoutMs: 5000 })
        .then(value => Boolean(value && typeof value === "object" ? value.granted : value));
      const requestOverlayPermission = () => call("ui.overlay", "openPermissionSettings", [], { permissions: ["ui.overlay.permission"], timeoutMs: 5000 }).then(done);
      const floaty = Object.freeze({
        window: openFloaty("window", false),
        rawWindow: openFloaty("rawWindow", true),
        closeAll: () => call("ui", "floatyCloseAll", [], { permissions: ["ui"] }).then(count => {
          for (const window of [...floatyWindows]) floatyWindows.delete(window);
          return Number(count) || 0;
        }),
        hasPermission: overlayPermission,
        checkPermission: overlayPermission,
        requestPermission: requestOverlayPermission,
        async ensurePermission() {
          if (await overlayPermission()) return;
          await requestOverlayPermission();
          throw Object.assign(new Error("floaty.ensurePermission: the overlay (draw over other apps) permission is not granted"), { code: "ERR_AUTOJS6_FLOATY_PERMISSION" });
        },
        // Rhino reads the clip through a focused floating window; the host clipboard route does that itself.
        getClip: () => call("clipboard", "getText", [], { permissions: ["clipboard"], timeoutMs: 5000 })
          .then(value => value === undefined || value === null ? "" : String(value))
      });

      // web: ByteString (okio's immutable bytes), WebSocket with Rhino's event names, injectable WebView / WebViewClient.
      // Binary payloads: a ByteString (of any facade instance), an ArrayBuffer or a view; anything else is sent as text.
      const bytesOf = value => value && typeof value === "object" && typeof value.toByteArray === "function" && typeof value.hex === "function" ? value.toByteArray()
        : value instanceof ArrayBuffer ? new Uint8Array(value) : ArrayBuffer.isView(value) ? new Uint8Array(value.buffer, value.byteOffset, value.byteLength) : null;
      const toBase64 = bytes => {
        let binary = "";
        for (let index = 0; index < bytes.length; index += 0x8000) binary += String.fromCharCode.apply(null, bytes.subarray(index, index + 0x8000));
        return btoa(binary);
      };
      const fromBase64 = text => Uint8Array.from(atob(text), char => char.charCodeAt(0));
      class ByteString {
        constructor(bytes) { Object.defineProperty(this, "bytes", { value: Uint8Array.from(bytes || []) }); Object.freeze(this); }
        static of(...bytes) { return new ByteString(bytes.map(byte => Number(byte) & 0xff)); }
        static encodeUtf8(text) { return new ByteString(new TextEncoder().encode(String(text))); }
        static encodeString(text, charset) {
          if (charset !== undefined && charset !== null && !/^utf-?8$/i.test(String(charset))) throw new RangeError("ByteString.encodeString supports UTF-8 only");
          return ByteString.encodeUtf8(text);
        }
        static decodeHex(hex) {
          const text = String(hex);
          if (text.length % 2 || /[^0-9a-f]/i.test(text)) throw new RangeError("Unexpected hex string: " + text);
          return new ByteString(text.match(/../g)?.map(pair => parseInt(pair, 16)) || []);
        }
        static decodeBase64(text) {
          try { return new ByteString(fromBase64(String(text).replace(/[-_]/g, char => char === "-" ? "+" : "/").replace(/\s+/g, ""))); }
          catch (_) { return null; }
        }
        static from(value) {
          if (value instanceof ByteString) return value;
          if (typeof value === "string") return ByteString.encodeUtf8(value);
          if (value instanceof ArrayBuffer) return new ByteString(new Uint8Array(value));
          if (ArrayBuffer.isView(value)) return new ByteString(new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
          if (Array.isArray(value)) return new ByteString(value.map(byte => Number(byte) & 0xff));
          throw new TypeError("ByteString.from requires a string, bytes or a byte array");
        }
        utf8() { return new TextDecoder().decode(this.bytes); }
        string(charset) {
          if (charset !== undefined && charset !== null && !/^utf-?8$/i.test(String(charset))) throw new RangeError("ByteString.string supports UTF-8 only");
          return this.utf8();
        }
        hex() { return Array.from(this.bytes, byte => byte.toString(16).padStart(2, "0")).join(""); }
        base64() { return toBase64(this.bytes); }
        base64Url() { return this.base64().replace(/[+/]/g, char => char === "+" ? "-" : "_"); }
        size() { return this.bytes.length; }
        getSize() { return this.bytes.length; }
        getByte(index) { const value = this.bytes[index]; if (value === undefined) throw new RangeError("ByteString index out of range"); return value << 24 >> 24; }
        toByteArray() { return Uint8Array.from(this.bytes); }
        toAsciiLowercase() { return new ByteString(this.bytes.map(byte => byte >= 65 && byte <= 90 ? byte + 32 : byte)); }
        toAsciiUppercase() { return new ByteString(this.bytes.map(byte => byte >= 97 && byte <= 122 ? byte - 32 : byte)); }
        substring(begin = 0, end = this.bytes.length) { return new ByteString(this.bytes.subarray(begin, end)); }
        startsWith(prefix) { const bytes = ByteString.from(prefix).bytes; return bytes.every((byte, index) => this.bytes[index] === byte); }
        endsWith(suffix) { const bytes = ByteString.from(suffix).bytes, offset = this.bytes.length - bytes.length; return offset >= 0 && bytes.every((byte, index) => this.bytes[offset + index] === byte); }
        indexOf(other, fromIndex = 0) { const bytes = ByteString.from(other).bytes; for (let index = Math.max(0, fromIndex); index + bytes.length <= this.bytes.length; index++) if (bytes.every((byte, offset) => this.bytes[index + offset] === byte)) return index; return -1; }
        equals(other) { return other instanceof ByteString && other.bytes.length === this.bytes.length && other.bytes.every((byte, index) => this.bytes[index] === byte); }
        toString() {
          if (this.bytes.length === 0) return "[size=0]";
          const text = this.utf8(), replacement = String.fromCharCode(0xfffd);
          const control = this.bytes.some(byte => byte < 0x20 && byte !== 0x09 && byte !== 0x0a && byte !== 0x0d);
          return !control && !text.includes(replacement) ? "[text=" + text + "]" : "[hex=" + this.hex() + "]";
        }
      }
      // Without a runtime connection: the host websocket routes, events drained here.
      async function hostConnect(url, socketOptions) {
        const descriptor = { url, headers: Object.entries(socketOptions.headers || {}).map(([name, value]) => [String(name), String(value)]),
          protocols: Array.isArray(socketOptions.protocols) ? socketOptions.protocols.map(String) : [], timeoutMs: Number(socketOptions.timeoutMs) || 30000 };
        const record = await call("websocket", "connect", [descriptor], { permissions: ["network"], timeoutMs: descriptor.timeoutMs, events: false });
        const listeners = new Map(), id = String(record && record.id || "");
        let closed = false;
        const emit = (name, ...args) => (listeners.get(name) || []).slice().forEach(listener => listener(...args));
        (async () => {
          while (!closed) {
            const events = await call("websocket", "drainEvents", [{ id }], { permissions: ["network"], timeoutMs: 5000 });
            for (const event of Array.isArray(events) ? events : []) {
              if (event.type === "message") emit("message", event.message || {});
              else if (event.type === "error") emit("error", Object.assign(new Error(event.error && event.error.message || "WebSocket failed"), { code: event.error && event.error.code }));
              else if (event.type === "close") { closed = true; emit("close", { code: event.code === undefined ? 1000 : event.code, reason: event.reason || "" }); }
            }
            if (!closed) await new Promise(resolve => setTimeout(resolve, 50));
          }
        })().catch(error => { if (!closed) { closed = true; emit("error", error); emit("close", { code: 1006, reason: String(error && error.message || error) }); } });
        return {
          on(name, listener) { listeners.set(name, (listeners.get(name) || []).concat(listener)); return this; },
          send(data) {
            const payload = typeof data === "string" ? { id, text: data } : { id, dataBase64: toBase64(data) };
            return call("websocket", "send", [payload], { permissions: ["network"], timeoutMs: 30000 }).then(done);
          },
          close(code, reason) {
            return call("websocket", "close", [{ id, code: code === undefined ? 1000 : Number(code), reason: reason === undefined ? "" : String(reason) }],
              { permissions: ["network"], timeoutMs: 30000 }).then(done);
          }
        };
      }
      function newWebSocket(url, socketOptions) {
        if (typeof url !== "string" || !url) throw new TypeError("web.newWebSocket requires a URL string");
        const opts = options("web.newWebSocket", socketOptions);
        const emitter = __autojs6_ui_emitter(), sticky = new Map();
        let connection = null, generation = 0, closed = false, rebuilds = 0, maxRebuilds = Infinity, exitOnClose = false, exitTimeout = 0, queued = 0;
        const pending = [];
        const emit = (name, ...args) => {
          try { return emitter.emit(name, ...args); }
          catch (error) { setTimeout(() => { throw error; }, 0); return true; }
        };
        const fail = error => emit("failure", error, null, socket);
        function deliver(payload) {
          const bytes = typeof payload === "string" ? payload.length : payload.length;
          queued += bytes;
          connection.send(payload).catch(fail).finally(() => { queued -= bytes; });
        }
        function connect() {
          const current = ++generation;
          closed = false; connection = null;
          (deps.connectWebSocket || hostConnect)(url, opts).then(value => {
            if (current !== generation) { value.close(1000, "").catch(() => {}); return; }
            connection = value;
            value.on("message", message => {
              if (current !== generation) return;
              if (message && typeof message.text === "string") { emit("message", message.text, socket); emit("text", message.text, socket); return; }
              const data = message && (message.dataBase64 !== undefined ? fromBase64(String(message.dataBase64)) : message.data);
              const bytes = new ByteString(data instanceof ArrayBuffer ? new Uint8Array(data) : data || []);
              emit("message", bytes, socket); emit("bytes", bytes, socket);
            });
            value.on("error", error => { if (current === generation) fail(error); });
            value.on("close", event => {
              if (current !== generation) return;
              closed = true; connection = null;
              const code = event && event.code !== undefined ? Number(event.code) : 1000, reason = event && event.reason || "";
              emit("closing", code, reason, socket);
              emit("closed", code, reason, socket);
              if (exitOnClose) setTimeout(exit, exitTimeout);
            });
            emit("open", Object.freeze({ url, protocol: value.protocol || "" }), socket);
            pending.splice(0).forEach(deliver);
          }, error => {
            if (current !== generation) return;
            closed = true; pending.length = 0;
            fail(error);
          });
        }
        const socket = {
          get url() { return url; },
          get client() { return null; },
          request: () => Object.freeze({ url, method: "GET", headers: Object.freeze({ ...(opts.headers || {}) }) }),
          send(data) {
            if (closed) return false;
            const bytes = bytesOf(data), payload = bytes || String(data);
            if (connection) deliver(payload); else pending.push(payload);
            return true;
          },
          close(code, reason) {
            if (closed) return false;
            if (typeof code === "string" && reason === undefined) { reason = code; code = 1000; }
            closed = true;
            const target = connection;
            if (target) target.close(code === undefined ? 1000 : Number(code), reason === undefined || reason === null ? "" : String(reason)).catch(fail);
            else { generation++; pending.length = 0; emit("closed", code === undefined ? 1000 : Number(code), reason || "", socket); }
            return true;
          },
          cancel() {
            const target = connection;
            generation++; closed = true; connection = null; pending.length = 0;
            if (target) target.close(1001, "").catch(() => {});
            fail(Object.assign(new Error("Canceled"), { code: "ERR_AUTOJS6_WEBSOCKET_CANCELED" }));
          },
          queueSize: () => queued + pending.reduce((total, item) => total + item.length, 0),
          rebuild(max) {
            if (max !== undefined) maxRebuilds = Math.max(0, Number(max) || 0);
            const target = connection;
            generation++; connection = null;
            if (target) target.close(1001, "").catch(() => {});
            if (rebuilds < maxRebuilds) { rebuilds++; connect(); }
            else { closed = true; emit("max_rebuilds", maxRebuilds, socket); }
          },
          exitOnClose(value) {
            if (typeof value === "number") { exitOnClose = true; exitTimeout = Math.max(0, value); }
            else exitOnClose = value === undefined ? true : Boolean(value);
          },
          getTimer: () => Object.freeze({ setTimeout, clearTimeout, setInterval, clearInterval }),
          emitSticky(name, ...args) { sticky.set(name, args); return emit(name, ...args); }
        };
        // EventEmitter members return the socket; a listener added after emitSticky receives the sticky arguments at once.
        for (const name of ["on", "addListener", "once", "prependListener", "prependOnceListener"]) {
          socket[name] = (eventName, listener) => {
            emitter[name](eventName, listener);
            if (sticky.has(eventName)) { const args = sticky.get(eventName); if (name.toLowerCase().includes("once")) emitter.removeListener(eventName, listener); listener.apply(socket, args); }
            return socket;
          };
        }
        for (const name of ["off", "removeListener", "removeAllListeners", "setMaxListeners"]) socket[name] = (...args) => { emitter[name](...args); return socket; };
        for (const name of ["emit", "listeners", "listenerCount", "eventNames", "getMaxListeners"]) socket[name] = (...args) => emitter[name](...args);
        connect();
        return Object.freeze(socket);
      }
      // InjectableWebClient: a WebView client whose inject() waits for the first page finished, as Rhino's does.
      function newInjectableWebClient() {
        let view = null;
        const waiting = [];
        const evaluate = (target, script) => target.evaluateJavascript(String(script)).then(value => value === undefined || value === null ? null : typeof value === "string" ? value : JSON.stringify(value));
        const client = {
          onPageFinished() {
            view = this;
            for (const task of waiting.splice(0)) evaluate(view, task.script).then(task.resolve, task.reject);
          }
        };
        function injectAndWait(script) {
          if (view) return evaluate(view, script);
          return new Promise((resolve, reject) => waiting.push({ script, resolve, reject }));
        }
        Object.defineProperties(client, {
          inject: { value: (script, callback) => {
            const result = injectAndWait(script);
            if (typeof callback === "function") result.then(value => callback(value), () => callback(null));
            else result.catch(() => {});
            return result;
          } },
          injectAndWait: { value: injectAndWait }
        });
        return Object.freeze(client);
      }
      async function newInjectableWebView(first, url) {
        const target = url !== undefined ? url : typeof first === "string" ? first : undefined;
        if (target !== undefined && target !== null && typeof target !== "string") throw new TypeError("web.newInjectableWebView url must be a string");
        const client = newInjectableWebClient();
        const view = await inflate({ type: "WebView", javaScriptEnabled: true, domStorageEnabled: true });
        await view.setWebViewClient(client);
        if (target) await view.loadUrl(target);
        return new Proxy(view, { get: (object, key) => key === "inject" ? client.inject : Reflect.get(object, key), has: (object, key) => key === "inject" || key in object });
      }
      const web = Object.freeze({ ByteString, newWebSocket, newInjectableWebView, newInjectableWebClient });

      // terminal: the 3-Shell Terminal plugin through the host (capability terminal); the Async forms are the same calls.
      function TerminalError(message, code, sessionId) {
        if (!(this instanceof TerminalError)) return new TerminalError(message, code, sessionId);
        const text = message === undefined || message === null ? "" : String(message);
        Object.defineProperty(this, "message", { value: text, writable: true, configurable: true });
        this.code = code === undefined || code === null ? "INTERNAL" : String(code);
        if (sessionId !== undefined && sessionId !== null) this.sessionId = String(sessionId);
        Object.defineProperty(this, "stack", { value: new Error(text).stack, writable: true, configurable: true });
      }
      TerminalError.prototype = Object.create(Error.prototype, {
        constructor: { value: TerminalError, writable: true, configurable: true },
        name: { value: "TerminalError", writable: true, configurable: true },
        toString: { value: function() { return this.name + " [" + this.code + "]: " + this.message; }, writable: true, configurable: true }
      });
      // Host failures carry the plugin's code (PLUGIN_UNAVAILABLE, TIMEOUT, ...); bridge and capability errors (ERR_*) stay as
      // they are. The session id comes from the call, the host does not repeat it.
      const terminalError = (error, id) => error && typeof error === "object" && !(error instanceof TerminalError)
        && /^[A-Z][A-Z0-9_]*$/.test(String(error.code || "")) && !String(error.code).startsWith("ERR_")
        ? Object.assign(new TerminalError(error.message, error.code, id), { cause: error }) : error;
      // exec / waitFor wait on the host up to 10 minutes by default; those calls pass their own timeout.
      const terminalCall = (method, args, timeoutMs, id) => call("terminal", method, args, { permissions: ["terminal"], ...(timeoutMs ? { timeoutMs } : {}) })
        .catch(error => { throw terminalError(error, id); });
      const sessionId = value => value && typeof value === "object" ? String(value.id) : String(value);
      function terminalSession(json) {
        if (!json || typeof json !== "object") return null;
        const id = String(json.id);
        const session = { ...json,
          isAlive: () => terminalCall("isAlive", [id], 0, id).then(Boolean),
          write(text) { return terminalCall("write", [id, text === undefined || text === null ? "" : String(text)], 0, id).then(() => session); },
          waitFor(pattern, timeout) {
            const value = pattern instanceof RegExp ? { regex: pattern.source, flags: pattern.flags } : String(pattern);
            const ms = timeout === undefined || timeout === null ? undefined : Number(timeout);
            return terminalCall("waitFor", ms === undefined ? [id, value] : [id, value, ms], ms === undefined ? 2147483647 : ms + 5000, id);
          },
          transcript: maxBytes => terminalCall("transcript", maxBytes === undefined ? [id] : [id, Number(maxBytes)], 0, id).then(String),
          show() { return terminalCall("show", [id], 0, id).then(() => session); },
          close: () => terminalCall("close", [id], 0, id).then(Boolean)
        };
        for (const name of ["write", "waitFor", "transcript", "show", "close"]) session[name + "Async"] = session[name];
        session.isAliveAsync = session.isAlive;
        session.toString = () => "TerminalSession { id: " + id + ", cwd: " + json.cwd + ", state: " + json.state + " }";
        return Object.freeze(session);
      }
      const execTimeout = execOptions => execOptions && typeof execOptions === "object" && Number(execOptions.timeout) > 0 ? Number(execOptions.timeout) + 10000 : 2147483647;
      const terminal = {
        TerminalError,
        open: settle(openOptions => terminalCall("open", openOptions === undefined ? [] : [openOptions], 60000).then(terminalSession)),
        sessions: () => terminalCall("sessions", []).then(list => (Array.isArray(list) ? list : []).map(terminalSession)),
        session: settle(id => terminalCall("session", [sessionId(id)], 0, sessionId(id)).then(terminalSession)),
        close: settle(id => terminalCall("close", [sessionId(id)], 0, sessionId(id)).then(Boolean)),
        closeAll: () => terminalCall("closeAll", []).then(Number),
        show: settle(id => id === undefined || id === null ? terminalCall("show", []).then(done) : terminalCall("show", [sessionId(id)], 0, sessionId(id)).then(done)),
        state: () => terminalCall("state", []).then(String),
        isAvailable: () => terminalCall("isAvailable", []).then(Boolean),
        exec: settle((command, execOptions) => terminalCall("exec", execOptions === undefined ? [command] : [command, execOptions], execTimeout(execOptions))),
        env: () => terminalCall("env", [])
      };
      for (const name of ["open", "sessions", "close", "closeAll", "show", "isAvailable", "exec", "env"]) terminal[name + "Async"] = terminal[name];
      const npmRun = settle((script, dir, runOptions) => terminalCall("npmRun", [script, ...(dir === undefined && runOptions === undefined ? [] : [dir === undefined ? null : dir]),
        ...(runOptions === undefined ? [] : [runOptions])], execTimeout(runOptions)));
      terminal.npm = Object.freeze({ run: npmRun, runAsync: npmRun });

      // console.build: Rhino's ConsoleImpl.Configurator recorded here; show() applies it in one host call (console.configure).
      const consoleSetters = {
        setSize: (state, width, height) => { state.size = Object.freeze({ width: Number(width), height: Number(height) }); },
        setPosition: (state, x, y) => { state.position = Object.freeze({ x: Number(x), y: Number(y) }); },
        setGravity: (state, gravity) => { state.gravity = gravity === undefined || gravity === null ? 0 : Number(gravity); },
        setTitle: (state, title) => { state.title = title === undefined || title === null ? "" : String(title); },
        setTitleTextSize: (state, size) => { state.titleTextSize = Number(size); },
        setTitleTextColor: (state, color) => { state.titleTextColor = color === undefined ? null : color; },
        setTitleBackgroundColor: (state, color) => { state.titleBackgroundColor = color === undefined ? null : color; },
        setTitleBackgroundTint: (state, color) => { state.titleBackgroundTint = color === undefined ? null : color; },
        setTitleBackgroundAlpha: (state, alpha) => { state.titleBackgroundAlpha = alpha === undefined || alpha === null ? 1 : Number(alpha); },
        setTitleIconsTint: (state, color) => { state.titleIconsTint = color === undefined ? null : color; },
        setContentTextSize: (state, size) => { state.contentTextSize = Number(size); },
        setContentTextColors: (state, ...colors) => { const list = Array.isArray(colors[0]) ? colors[0] : colors; state.contentTextColors = Object.freeze(list.slice(0, 6)); },
        setContentBackgroundColor: (state, color) => { state.contentBackgroundColor = color === undefined ? null : color; },
        setContentBackgroundTint: (state, color) => { state.contentBackgroundTint = color === undefined ? null : color; },
        setContentBackgroundAlpha: (state, alpha) => { state.contentBackgroundAlpha = alpha === undefined || alpha === null ? 1 : Number(alpha); },
        setTextSize: (state, size) => { consoleSetters.setTitleTextSize(state, size); consoleSetters.setContentTextSize(state, size); },
        setTextColor: (state, color) => { consoleSetters.setTitleTextColor(state, color); state.contentTextColors = Object.freeze(Array(6).fill(color)); },
        setBackgroundColor: (state, color) => { consoleSetters.setTitleBackgroundColor(state, color); consoleSetters.setContentBackgroundColor(state, color); },
        setBackgroundTint: (state, color) => { consoleSetters.setTitleBackgroundTint(state, color); consoleSetters.setContentBackgroundTint(state, color); },
        setBackgroundAlpha: (state, alpha) => { consoleSetters.setTitleBackgroundAlpha(state, alpha); consoleSetters.setContentBackgroundAlpha(state, alpha); },
        setExitOnClose: (state, value) => {
          if (typeof value === "number") { state.isExitOnClose = true; state.exitOnCloseTimeout = value; }
          else state.isExitOnClose = value === undefined ? true : Boolean(value);
        },
        setTouchable: (state, value) => { state.isTouchable = value === undefined ? true : Boolean(value); },
        setTouchThrough: (state, value) => { state.isTouchable = !(value === undefined ? true : Boolean(value)); },
        setTimeVisible: (state, value) => { state.isTimeVisible = value === undefined ? false : Boolean(value); },
        setTimeFormat: (state, pattern) => { state.timeFormat = pattern === undefined || pattern === null ? null : String(pattern); },
        setColorful: (state, value) => { state.isColorful = value === undefined ? true : Boolean(value); },
        setAvoidStatusBar: (state, value) => { state.isAvoidStatusBar = value === undefined ? true : Boolean(value); },
        setInputVisible: (state, value) => { state.isInputVisible = value === undefined ? true : Boolean(value); }
      };
      const consoleValues = ["size", "position", "gravity", "title", "titleTextSize", "titleTextColor", "titleBackgroundColor", "titleBackgroundTint",
        "titleBackgroundAlpha", "titleIconsTint", "contentTextSize", "contentTextColors", "contentBackgroundColor", "contentBackgroundTint",
        "contentBackgroundAlpha", "exitOnCloseTimeout", "timeFormat"];
      const consoleFlags = ["isTouchable", "isExitOnClose", "isTimeVisible", "isColorful", "isAvoidStatusBar", "isInputVisible"];
      const consoleOptionKeys = { isTouchable: "touchable", isTimeVisible: "timeVisible", isColorful: "colorful", isAvoidStatusBar: "avoidStatusBar", isInputVisible: "inputVisible" };
      function ConsoleConfigurator() {
        if (!(this instanceof ConsoleConfigurator)) return new ConsoleConfigurator();
        Object.defineProperty(this, "__autojs6ConsoleState", { value: Object.create(null) });
      }
      for (const [name, setter] of Object.entries(consoleSetters)) {
        ConsoleConfigurator.prototype[name] = function(...args) { setter(this.__autojs6ConsoleState, ...args); return this; };
      }
      for (const name of consoleValues) {
        Object.defineProperty(ConsoleConfigurator.prototype, name, { get() { const value = this.__autojs6ConsoleState[name]; return value === undefined ? null : value; }, enumerable: true });
      }
      for (const name of consoleFlags) {
        ConsoleConfigurator.prototype[name] = function() { const value = this.__autojs6ConsoleState[name]; return value === undefined ? null : value; };
      }
      // The recorded values as console.configure options (keys of Rhino's Configurator fields); unset keys keep the window's.
      ConsoleConfigurator.prototype.toOptions = function() {
        const state = this.__autojs6ConsoleState, output = {};
        for (const name of consoleValues) if (state[name] !== undefined && name !== "exitOnCloseTimeout") output[name] = state[name];
        for (const name of consoleFlags) if (state[name] !== undefined && consoleOptionKeys[name]) output[consoleOptionKeys[name]] = state[name];
        if (state.isExitOnClose !== undefined) output.exitOnClose = state.isExitOnClose && state.exitOnCloseTimeout !== undefined ? state.exitOnCloseTimeout : state.isExitOnClose;
        return output;
      };
      ConsoleConfigurator.prototype.show = function(isReset) {
        return call("console", "configure", [{ ...this.toOptions(), ...(isReset ? { reset: true } : {}) }], { permissions: ["console"] }).then(done);
      };
      // Rhino's console.build(options): each key names a setter (size -> setSize, touchable -> setTouchable / isTouchable).
      function build(buildOptions) {
        const configurator = new ConsoleConfigurator(), opts = options("console.build", buildOptions);
        for (const [key, value] of Object.entries(opts)) {
          const setter = /^set[A-Z]/.test(key) ? key : "set" + key.charAt(0).toUpperCase() + key.slice(1);
          if (!consoleSetters[setter]) throw new TypeError("Unknown key \"" + key + "\" of options for console.build");
          configurator[setter](...(Array.isArray(value) && setter !== "setContentTextColors" ? value : [value]));
        }
        return configurator;
      }
      // Rhino's UI-family globals as accessors over the facades (`sources.toast()` / `sources.compose()`, read on first use; the
      // system globals' precedent, Q135 (b)); assigning one replaces it with a plain value, names the target has stay.
      function installGlobals(target, sources) {
        const entries = {
          isUiThread: () => () => false,
          newWebSocket: () => newWebSocket, newInjectableWebView: () => newInjectableWebView, newInjectableWebClient: () => newInjectableWebClient,
          ComposeError: () => sources.compose().ComposeError
        };
        for (const level of ["Log", "Info", "Warn", "Error", "Verbose"]) {
          entries["toast" + level] = () => sources.toast()["toast" + level];
          entries["toast" + level.toLowerCase()] = entries["toast" + level];
        }
        const names = Object.keys(entries).filter(name => !Object.prototype.hasOwnProperty.call(target, name));
        for (const name of names) {
          Object.defineProperty(target, name, {
            configurable: true, enumerable: false, get: () => entries[name](),
            set(value) { Object.defineProperty(target, name, { value, writable: true, configurable: true, enumerable: false }); }
          });
        }
        return names;
      }
      return Object.freeze({ floaty, web, terminal, console: Object.freeze({ build, ConsoleConfigurator }), installGlobals });
    }
  function __autojs6_limited_ui() {
    if (__autojs6_limited_ui_cache) {
      return __autojs6_limited_ui_cache;
    }
    function showLayout(layoutJson, options) {
      let prepared;
      let descriptor;
      try {
        if (typeof layoutJson === "string" || layoutJson && layoutJson.__autojs6ViewHandle) {
          prepared = { bindings: [] }; descriptor = layoutJson;
        } else {
          prepared = __autojs6_ui_prepare_descriptor(layoutJson, "showLayout");
          descriptor = __autojs6_ui_descriptor(prepared.descriptor, "showLayout");
        }
      } catch (error) {
        return Promise.reject(error);
      }
      const bridgeOptions = __autojs6_ui_options(options);
      return __autojs6_call_autojs(
        "ui",
        "showLayout",
        [descriptor],
        __autojs6_ui_bridge_options(bridgeOptions, 10000)
      ).then(function(result) {
        return __autojs6_ui_handle(result, bridgeOptions, prepared.bindings);
      });
    }
    const api = {
      showLayout, inflate, createView, context, view: wrapView, findView,
      state: __autojs6_ui_state,
      bind: __autojs6_ui_bind,
      ...__autojs6_ui_rhino_members
    };
    Object.defineProperties(api, __autojs6_ui_rhino_getters);
    // Not enumerable: the runtime facades build their floaty / web / terminal modules and console.build from it.
    Object.defineProperty(api, "__autojs6Family", { value: __autojs6_ui_family });
    __autojs6_limited_ui_cache = Object.freeze(api);
    return __autojs6_limited_ui_cache;
  }
    return __autojs6_limited_ui();
  }
  function __autojs6_create_compose(call) {
    const transport = call;
    call = async (module, method, args, options) => transport(module, method, JSON.parse(JSON.stringify(args, (_key, value) => {
      if (typeof value === "number" && !Number.isFinite(value)) throw new TypeError("Compose arguments must contain finite numbers");
      if (["function", "symbol", "bigint"].includes(typeof value)) throw new TypeError("Compose arguments must be JSON values");
      return value;
    })), options);
    const names = ["AndroidView", "AlertDialog", "Box", "Button", "Card", "Checkbox", "CircularProgressIndicator", "Column", "ElevatedButton", "FilledTonalButton", "HorizontalDivider", "Icon", "IconButton", "Image", "LazyColumn", "LazyRow", "LinearProgressIndicator", "OutlinedButton", "OutlinedTextField", "RadioButton", "Row", "Scaffold", "Slider", "Spacer", "Surface", "Switch", "Text", "TextButton", "TextField", "TopAppBar", "AssistChip", "Badge", "DatePicker", "DropdownMenu", "DropdownMenuItem", "FilterChip", "FloatingActionButton", "HorizontalPager", "InputChip", "LazyVerticalGrid", "ModalBottomSheet", "NavigationBar", "NavigationBarItem", "NavigationDrawer", "NavigationDrawerItem", "NavigationRail", "NavigationRailItem", "PullToRefresh", "SearchBar", "SegmentedButton", "SegmentedButtonItem", "Tab", "TabRow", "TimePicker", "Tooltip"];
    const modifierNames = ["padding", "size", "width", "height", "fillMaxWidth", "fillMaxHeight", "fillMaxSize", "weight", "align", "background", "border", "clip", "alpha", "clickable", "verticalScroll", "horizontalScroll", "offset", "aspectRatio", "testTag", "semantics"];
    const nodeTag = Symbol("compose.node"), modifierTag = Symbol("compose.modifier");
    const open = new Set();
    let nextNode = 1, nextCallback = 1, active = null, batchDepth = 0, revision = 0, defaultTheme;
    const dirty = new Set();
    // api-coverage P3.3: Rhino's ComposeError (ComposeJsErrors), thrown by this facade with the code INVALID_ARGUMENT.
    function ComposeError(message, code, nodeId, prop, cause) {
      if (!(this instanceof ComposeError)) return new ComposeError(message, code, nodeId, prop, cause);
      Object.defineProperty(this, "message", { value: String(message || ""), writable: true, configurable: true });
      this.code = code || "INVALID_ARGUMENT";
      this.nodeId = nodeId == null ? null : nodeId;
      this.prop = prop == null ? null : prop;
      this.cause = cause == null ? null : cause;
      this.retryable = ["PLUGIN_UNAVAILABLE", "PLUGIN_DISABLED", "PLUGIN_UNAUTHORIZED", "PLUGIN_INCOMPATIBLE"].includes(this.code);
      Object.defineProperty(this, "stack", { value: new Error(this.message).stack, configurable: true });
    }
    ComposeError.prototype = Object.create(Error.prototype, {
      constructor: { value: ComposeError, writable: true, configurable: true },
      name: { value: "ComposeError", configurable: true }
    });
    const error = message => new ComposeError(message, "INVALID_ARGUMENT");
    // The shortcut props Rhino's ComposeNode reads and writes as properties (Q104).
    const shortcutProps = ["w", "h", "padding", "bg", "weight", "alpha", "testTag", "contentDescription"];
    const adopt = (parent, values) => { for (const value of values) if (value && value[nodeTag]) value[nodeTag].parent = parent; return values; };
    const release = values => { for (const value of values) if (value && value[nodeTag]) value[nodeTag].parent = null; return values; };
    const notify = session => { if (!session.closed) { dirty.add(session); if (!batchDepth) queueMicrotask(flushDirty); } };
    function flushDirty() { if (batchDepth) return; for (const session of dirty) { dirty.delete(session); session.schedule(); } }
    function children(values) {
      return values.flat(Infinity).filter(value => value !== null && value !== undefined && value !== false).map(value => {
        if (typeof value === "string") return createElement("Text", { text: value });
        if (!value || !value[nodeTag]) throw error("Compose children must be nodes or text");
        return value;
      });
    }
    function createElement(type, props, ...args) {
      if (!names.includes(type)) throw error("Unsupported Compose component: " + type);
      if (props && props[nodeTag] || typeof props === "string" || Array.isArray(props)) { args.unshift(props); props = {}; }
      props = { ...(props || {}) };
      const state = { nodeId: nextNode++, type, props, children: children(args.length ? args : [props.children]), owners: new Set(), listeners: new Map(), editSeq: 0, parent: null };
      delete props.children;
      const change = () => { revision++; for (const owner of state.owners) notify(owner); return node; };
      const command = async (type, value = {}) => {
        if (state.owners.size !== 1) throw error("A command requires one active Compose owner");
        const session = [...state.owners][0]; await session.flush();
        await call("compose", "command", [session.id, { nodeId: state.nodeId, type, ...value }]);
      };
      const node = {
        [nodeTag]: state,
        set(value) { Object.assign(props, value); return change(); },
        get(name) { return props[name]; },
        append(value) { state.children.push(...adopt(node, children([value]))); return change(); },
        insert(index, value) { if (!Number.isInteger(index) || index < 0 || index > state.children.length) throw error("Invalid child index"); state.children.splice(index, 0, ...adopt(node, children([value]))); return change(); },
        remove(value) { const index = typeof value === "number" ? value : state.children.indexOf(value); if (index < 0 || index >= state.children.length) throw error("Child not found"); release(state.children.splice(index, 1)); return change(); },
        replace(value) { release(state.children); state.children = adopt(node, children([value])); return change(); },
        clear() { release(state.children); state.children = []; return change(); },
        slot(name, child) { props.slots = { ...props.slots, [name]: child }; adopt(node, [child]); return change(); },
        on(name, callback) { if (typeof callback !== "function") throw error("Callback must be a function"); const event = name.replace(/^on([A-Z])/, (_, c) => c.toLowerCase()); state.listeners.set(event, [...state.listeners.get(event) || [], callback]); return change(); },
        off(name, callback) { const event = name.replace(/^on([A-Z])/, (_, c) => c.toLowerCase()); state.listeners.set(event, callback ? (state.listeners.get(event) || []).filter(fn => fn !== callback) : []); return change(); },
        focus: () => command("focus"), blur: () => command("blur"),
        scrollTo: target => command("scrollTo", typeof target === "number" ? { index: target } : target),
        edit: value => command("edit", { ...value, editSeq: state.editSeq }),
        toString: () => "[Compose " + type + "]"
      };
      // Rhino ComposeNode's own read-only members and the shortcut accessors (a write goes through set, Q104 / Q106).
      Object.defineProperties(node, {
        type: { get: () => type, enumerable: true },
        key: { get: () => props.key === undefined ? null : props.key, enumerable: true },
        nodeId: { get: () => state.nodeId, enumerable: true },
        // Not enumerable: parent / children link the tree both ways (JSON.stringify of a node stays acyclic).
        parent: { get: () => state.parent },
        children: { get: () => Object.freeze(state.children.slice()) }
      });
      for (const name of shortcutProps) Object.defineProperty(node, name, { get: () => props[name], set: value => { node.set({ [name]: value }); } });
      adopt(node, state.children);
      return Object.freeze(node);
    }
    function modifier(operations = []) {
      const result = { [modifierTag]: operations };
      for (const name of modifierNames) result[name] = (...args) => {
        if (operations.length >= 64) throw error("Too many modifier operations");
        return modifier([...operations, { name, args }]);
      };
      return Object.freeze(result);
    }
    function state(initial) {
      let value = initial; const readers = new Set();
      const source = { readers, version: 0 };
      return Object.freeze(Object.defineProperties({}, {
        value: { enumerable: true, get() { if (active) { readers.add(active); active.dependencies.add(source); for (const collector of active.readCollectors) collector.add(source); } return value; },
          set(next) { if (Object.is(value, next)) return; value = next; source.version++; revision++; for (const reader of readers) notify(reader); } }
      }));
    }
    function batch(fn) { if (typeof fn !== "function") throw error("batch requires a callback"); batchDepth++; try { return fn(); } finally { if (!--batchDepth) flushDirty(); } }
    function memo(build, deps, options = {}) {
      if (!active || !Array.isArray(deps) || deps.length > 256) throw error("memo requires an active render and at most 256 dependencies");
      const parent = active.memoPath;
      const index = active.memoIndices.get(parent) || 0; active.memoIndices.set(parent, index + 1);
      const key = parent + "/" + (options.key === undefined ? "index:" + index : "key:" + JSON.stringify(options.key));
      if (active.memoSeen.has(key) || active.memoSeen.size >= 5000) throw error("Duplicate or excessive memo keys");
      active.memoSeen.add(key);
      const old = active.memo.get(key);
      if (old && old.reads.every(([source, version]) => source.version === version) && deps.length === old.deps.length && deps.every((v, i) => Object.is(v, old.deps[i]))) {
        for (const [source] of old.reads) { source.readers.add(active); active.dependencies.add(source); for (const collector of active.readCollectors) collector.add(source); }
        return old.node;
      }
      const reads = new Set(); active.readCollectors.push(reads); active.memoPath = key;
      try { const node = build(); active.memo.set(key, { deps: deps.slice(), node, reads: [...reads].map(source => [source, source.version]) }); return node; }
      finally { active.readCollectors.pop(); active.memoPath = parent; }
    }
    function post(fn, delay = 0) {
      if (typeof fn !== "function" || !Number.isInteger(delay) || delay < 0 || delay > 2147483647) throw error("post requires a callback and a valid delay");
      return setTimeout(fn, delay);
    }
    async function mount(root, options = {}, mode = "mount", container) {
      if (active) throw error("Cannot mount while rendering");
      const session = { id: null, closed: false, root: null, dependencies: new Set(), nodes: new Set(), refs: new Set(), memo: new Map(), memoPath: "root", memoIndices: new Map(), memoSeen: new Set(), readCollectors: [],
        callbacks: new Map(), commands: new Map(), frames: new Map(), pendingEvents: [], generation: 0, listeners: new Map(), timers: new Set(), chain: Promise.resolve(), scheduled: false };
      function detach() {
        for (const dependency of session.dependencies) dependency.readers.delete(session);
        session.dependencies.clear();
        for (const node of session.nodes) node[nodeTag].owners.delete(session);
        session.nodes.clear(); for (const ref of session.refs) ref.current = null; session.refs.clear();
      }
      function finish() { if (session.closed) return; session.closed = true; open.delete(handle); dirty.delete(session); detach(); session.callbacks.clear(); session.commands.clear(); session.frames.clear(); session.pendingEvents=[]; session.memo.clear(); for (const timer of session.timers) clearTimeout(timer); session.timers.clear(); }
      function event(value) {
        if (session.closed) return;
        if (session.commands.has(value.callbackId)) {
          const callback = session.commands.get(value.callbackId); session.commands.delete(value.callbackId); callback(value); return;
        }
        if (value.generation > session.generation) { session.pendingEvents.push(value); return; }
        if (value.type === "frame") {
          for (const generation of session.frames.keys()) if (generation < value.generation) session.frames.delete(generation);
          return;
        }
        const callback = (session.frames.get(value.generation) || session.callbacks).get(value.callbackId);
        if (callback) { if (value.editSeq !== undefined) callback.node[nodeTag].editSeq = value.editSeq; callback.fn.call(callback.node, value); }
        for (const listener of [...session.listeners.get(value.type) || [], ...session.listeners.get("*") || []]) listener(value);
        if (value.type === "close") finish();
      }
      async function serialize() {
        for (const dependency of session.dependencies) dependency.readers.delete(session);
        session.dependencies.clear(); session.memoIndices.clear(); session.memoSeen.clear();
        let value;
        active = session; try { value = typeof root === "function" ? root() : root; } finally { active = null; }
        const callbacks = new Map(), nodes = new Set(), refs = new Map(), factories = [];
        function token(node, fn) { if (nextCallback >= 2147483647) throw error("Compose callback limit reached"); const id = nextCallback++; callbacks.set(id, { node, fn }); return id; }
        function walk(node, depth = 0) {
          if (!node || !node[nodeTag] || nodes.has(node) || depth > 64 || nodes.size >= 5000) throw error("Invalid, repeated or excessive Compose tree");
          nodes.add(node); const data = node[nodeTag];
          if ([...data.owners].some(owner => owner !== session)) throw error("Compose node belongs to another session");
          const props = {}, slots = {}, events = {};
          for (const [name, value] of Object.entries(data.props)) {
            if (name === "ref") { if (!value || typeof value !== "object") throw error("Invalid Compose ref"); refs.set(value, node); }
            else if (name === "slots") { for (const [key, child] of Object.entries(value || {})) { const list = children([child]); if (list.length > 1) throw error("A slot requires a single root"); if (list.length) slots[key] = walk(list[0], depth + 1); } }
            else if (data.type === "AndroidView" && name === "view" && typeof value === "function") {
              if (data.viewFactory !== value) { data.viewFactory = value; data.resolvedView = undefined; }
              factories.push((async () => {
                if (!data.resolvedView) {
                  const token = await call("ui", "context", []);
                  const scope = Object.freeze({ ...token, inflate: layout => call("ui", "inflate", [layout, token]),
                    createView: (type, props = {}) => call("ui", "inflate", [{ ...props, type: String(type).replace(/^android\.widget\./, "") }, token]) });
                  data.resolvedView = Promise.resolve(value(scope));
                }
                props.view = await data.resolvedView;
              })());
            }
            else if (name === "modifier" || name === "key") continue;
            else if (/^on[A-Z]/.test(name)) { if (typeof value !== "function") throw error("Event callback must be a function"); events[name] = token(node, value); }
            else if (value && value[nodeTag]) slots[name] = walk(value, depth + 1);
            else props[name] = value;
          }
          for (const [name, list] of data.listeners) if (list.length) {
            const key = "on" + name[0].toUpperCase() + name.slice(1), prior = data.props[key];
            delete events[key]; events[name] = token(node, function(e) { if (prior) prior.call(node, e); for (const fn of list) fn.call(node, e); });
          }
          const operations = data.props.modifier ? data.props.modifier[modifierTag] : [];
          if (!operations) throw error("Invalid modifier chain");
          return { nodeId: data.nodeId, type: data.type, ...(data.props.key === undefined ? {} : { key: String(data.props.key) }), props, slots, callbacks: events,
            modifier: operations.map(op => ({ name: op.name, args: op.args.map(value => typeof value === "function" ? token(node, value) : value) })),
            children: data.children.map(child => walk(child, depth + 1)) };
        }
        const descriptor = walk(value);
        await Promise.all(factories);
        for (const key of session.memo.keys()) if (!session.memoSeen.has(key)) session.memo.delete(key);
        return { descriptor, accept(generation) { for (const node of session.nodes) node[nodeTag].owners.delete(session); for (const ref of session.refs) ref.current = null;
          session.nodes = nodes; for (const node of nodes) node[nodeTag].owners.add(session);
          session.refs = new Set(refs.keys()); for (const [ref, node] of refs) ref.current = node;
          session.root = value; session.callbacks = callbacks;
          session.generation = generation === undefined ? session.generation + 1 : generation;
          session.frames.set(session.generation, callbacks);
          const pending = session.pendingEvents; session.pendingEvents = []; for (const value of pending) event(value); } };
      }
      session.schedule = () => {
        if (session.closed || session.scheduled) return;
        if (!session.id) { session.needsRender = true; return; }
        session.scheduled = true;
        session.chain = session.chain.catch(() => {}).then(async () => {
          session.scheduled = false; if (session.closed) return;
          const frame = await serialize(); const result = await call("compose", "render", [session.id, frame.descriptor]); if (!session.closed) frame.accept(result && result.generation);
        });
        session.chain.catch(failure => { event({ type: "error", message: failure.message }); });
      };
      session.flush = async () => { flushDirty(); await session.chain; };
      const handle = Object.freeze({
        get id() { return session.id; }, get root() { return session.root; }, get session() { return handle; }, isClosed: () => session.closed,
        update(fn) { if (session.closed) throw error("Compose session is closed"); const value = batch(fn); notify(session); return value; },
        flush: session.flush,
        render(value) { if (session.closed) return Promise.reject(error("Compose session is closed")); root = value; notify(session); return session.flush(); },
        setTheme: value => call("compose", "setTheme", [session.id, value]),
        async showSnackbar(message, options = {}) {
          if (session.closed) throw error("Compose session is closed");
          const callbackId = nextCallback++;
          session.commands.set(callbackId, value => { const fn = value.type === "action" ? options.onAction : options.onDismiss; if (fn) fn.call(handle); });
          try { await call("compose", "command", [session.id, { type: "snackbar", message, callbackId,
            ...(options.actionLabel === undefined ? {} : { actionLabel: options.actionLabel }), duration: options.duration || "short" }]); }
          catch (failure) { session.commands.delete(callbackId); throw failure; }
        },
        ...(mode === "floaty" ? {
          setPosition: async (x, y) => { await call("compose", "window", [session.id, { x, y }]); },
          setSize: async (width, height) => { await call("compose", "window", [session.id, { width, height }]); },
          requestFocus: async () => { await call("compose", "window", [session.id, { focusable: true }]); },
          disableFocus: async () => { await call("compose", "window", [session.id, { focusable: false }]); },
          setTouchable: async touchable => { await call("compose", "window", [session.id, { touchable }]); },
          getX: async () => (await call("compose", "window", [session.id, {}])).x,
          getY: async () => (await call("compose", "window", [session.id, {}])).y,
          getWidth: async () => (await call("compose", "window", [session.id, {}])).width,
          getHeight: async () => (await call("compose", "window", [session.id, {}])).height,
          // The window has no adjustment frame (Rhino's raw windows): disabling it is a no-op, enabling it is refused.
          setAdjustEnabled: async enabled => { if (enabled) throw error("Compose floating windows of the runtimes have no adjustment controls (as Rhino raw windows)"); },
        } : {}),
        post(fn, delay) { const timer = post(() => { session.timers.delete(timer); if (!session.closed) fn(); }, delay); session.timers.add(timer); },
        on(name, fn) { if (typeof fn !== "function") throw error("Listener must be a function"); session.listeners.set(name, [...session.listeners.get(name) || [], fn]); return handle; },
        off(name, fn) { session.listeners.set(name, (session.listeners.get(name) || []).filter(value => value !== fn)); return handle; },
        async close() { if (session.closed) return; await call("compose", "close", [session.id]); event({ type: "close" }); }
      });
      try {
        const frame = await serialize();
        const arguments_ = [frame.descriptor, { ...(defaultTheme ? { theme: defaultTheme } : {}), ...options }];
        if (mode === "attach") arguments_.unshift(container);
        const result = await call("compose", mode, arguments_);
        session.id = result.id; frame.accept(result.generation); open.add(handle);
        if (session.needsRender) session.schedule();
        let shown = false;
        const deadline = Date.now() + 10000;
        while (!shown && !session.closed) {
          const events = await call("compose", "drainEvents", [session.id]);
          for (const value of events) { if (value.type === "error") throw error(value.message); if (value.type === "shown") shown = true; event(value); }
          if (Date.now() >= deadline) throw error("Compose window did not become visible");
        }
        if (!shown || session.closed) throw error("Compose window closed before becoming visible");
        (async () => { while (!session.closed) { const values = await call("compose", "drainEvents", [session.id]); for (const value of values) event(value); } })()
          .catch(failure => { if (!session.closed) { event({ type: "error", message: failure.message }); call("compose", "close", [session.id]).catch(() => {}); finish(); } });
        return handle;
      } catch (failure) { if (session.id) await call("compose", "close", [session.id]).catch(() => {}); finish(); throw failure; }
    }
    const api = (root, options) => mount(root, options);
    Object.assign(api, { createElement, modifier, state, ref: () => ({ current: null }), batch, memo, post, ComposeError,
      mount, attach: (container, root, options) => mount(root, options, "attach", container), dialog: (root, options) => mount(root, options, "dialog"), floaty: (root, options) => mount(root, options, "floaty"),
      isAvailable: () => call("compose", "isAvailable", []), version: () => call("compose", "version", []),
      theme(value) { if (arguments.length) defaultTheme = value; return defaultTheme; }
    });
    for (const type of names) api[type] = (...args) => createElement(type, ...args);
    Object.defineProperty(api, "sessions", { enumerable: true, get: () => Object.freeze([...open]) });
    return Object.freeze(api);
  }
