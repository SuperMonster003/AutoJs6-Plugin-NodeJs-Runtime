  function __autojs6_create_ui(__autojs6_call_autojs, __autojs6_bridge_attach_events = () => null) {
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
      const emit = event => {
        if (closed) return;
        if (event.type === "close") closed = true;
        for (const callback of [...listeners.get(event.type) || [], ...listeners.get("*") || []]) callback.call(output, Object.freeze(event));
        if (closed) { listeners.clear(); nativeViews.delete(value.__autojs6ViewHandle); }
      };
      async function poll() {
        if (reading || closed) return; reading = true;
        try {
          while (!closed && [...listeners.values()].some(list => list.length)) {
            for (const event of await nativeCall("viewEvents", [value])) emit(event);
          }
        } catch (error) { emit({ type: "error", message: error.message }); emit({ type: "close" }); }
        finally { reading = false; }
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
        evaluateJavascript: source => command("evaluateJavascript", [source]),
        goBack: () => command("goBack", []), goForward: () => command("goForward", []),
        reload: () => command("reload", []), stopLoading: () => command("stopLoading", []),
        openDrawer: (side = "start", animate = true) => command("openDrawer", [side, animate]),
        closeDrawer: (side = "start", animate = true) => command("closeDrawer", [side, animate]),
        setCurrentItem: (index, smoothScroll = true) => command("setCurrentItem", [index, smoothScroll]),
        showOverflowMenu: () => command("showOverflowMenu", []), hideOverflowMenu: () => command("hideOverflowMenu", [])
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
    __autojs6_limited_ui_cache = Object.freeze({
      showLayout, inflate, createView, context, view: wrapView, findView,
      state: __autojs6_ui_state,
      bind: __autojs6_ui_bind
    });
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
    const error = message => Object.assign(new Error(message), { code: "ERR_AUTOJS6_COMPOSE_INVALID_ARGUMENT" });
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
      const state = { nodeId: nextNode++, type, props, children: children(args.length ? args : [props.children]), owners: new Set(), listeners: new Map(), editSeq: 0 };
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
        append(value) { state.children.push(...children([value])); return change(); },
        insert(index, value) { if (!Number.isInteger(index) || index < 0 || index > state.children.length) throw error("Invalid child index"); state.children.splice(index, 0, ...children([value])); return change(); },
        remove(value) { const index = typeof value === "number" ? value : state.children.indexOf(value); if (index < 0 || index >= state.children.length) throw error("Child not found"); state.children.splice(index, 1); return change(); },
        replace(value) { state.children = children([value]); return change(); },
        clear() { state.children = []; return change(); },
        slot(name, child) { props.slots = { ...props.slots, [name]: child }; return change(); },
        on(name, callback) { if (typeof callback !== "function") throw error("Callback must be a function"); const event = name.replace(/^on([A-Z])/, (_, c) => c.toLowerCase()); state.listeners.set(event, [...state.listeners.get(event) || [], callback]); return change(); },
        off(name, callback) { const event = name.replace(/^on([A-Z])/, (_, c) => c.toLowerCase()); state.listeners.set(event, callback ? (state.listeners.get(event) || []).filter(fn => fn !== callback) : []); return change(); },
        focus: () => command("focus"), blur: () => command("blur"),
        scrollTo: target => command("scrollTo", typeof target === "number" ? { index: target } : target),
        edit: value => command("edit", { ...value, editSeq: state.editSeq }),
        toString: () => "[Compose " + type + "]"
      };
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
    Object.assign(api, { createElement, modifier, state, ref: () => ({ current: null }), batch, memo, post,
      mount, attach: (container, root, options) => mount(root, options, "attach", container), dialog: (root, options) => mount(root, options, "dialog"), floaty: (root, options) => mount(root, options, "floaty"),
      isAvailable: () => call("compose", "isAvailable", []), version: () => call("compose", "version", []),
      theme(value) { if (arguments.length) defaultTheme = value; return defaultTheme; }
    });
    for (const type of names) api[type] = (...args) => createElement(type, ...args);
    Object.defineProperty(api, "sessions", { enumerable: true, get: () => Object.freeze([...open]) });
    return Object.freeze(api);
  }
