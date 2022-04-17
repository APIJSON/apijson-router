/*Copyright ©2016 TommyLemon(https://github.com/TommyLemon/APIJSON)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package apijson.router;

import static apijson.RequestMethod.GET;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.servlet.http.HttpSession;

import com.alibaba.fastjson.JSONObject;

import apijson.JSON;
import apijson.JSONRequest;
import apijson.Log;
import apijson.RequestMethod;
import apijson.StringUtil;
import apijson.framework.APIJSONConstant;
import apijson.framework.APIJSONController;
import apijson.framework.APIJSONCreator;
import apijson.framework.APIJSONParser;
import apijson.orm.AbstractParser;
import apijson.orm.AbstractVerifier;
import apijson.orm.Parser;
import apijson.orm.SQLConfig;
import apijson.orm.Verifier;


/**APIJSON router controller，建议在子项目被 @RestController 注解的类继承它或通过它的实例调用相关方法
 * <br > 全通过 HTTP POST 来请求:
 * <br > 1.减少代码 - 客户端无需写 HTTP GET, HTTP PUT 等各种方式的请求代码
 * <br > 2.提高性能 - 无需 URL encode 和 decode
 * <br > 3.调试方便 - 建议使用 APIAuto-机器学习自动化接口管理工具(https://github.com/TommyLemon/APIAuto)
 * @author Lemon
 */
public class APIJSONRouterController extends APIJSONController {
	public static final String TAG = "APIJSONRouterController";

	public String parseByTag(RequestMethod method, String tag, Map<String, String> params, String request, HttpSession session) {

		JSONObject req = AbstractParser.wrapRequest(method, tag, JSON.parseObject(request), false);
		if (req == null) {
			req = new JSONObject(true);
		}
		if (params != null && params.isEmpty() == false) {
			req.putAll(params);
		}

		return newParser(session, method).parse(req);
	}

	//通用接口，非事务型操作 和 简单事务型操作 都可通过这些接口自动化实现<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	// method-tag, <version, Document>
	Map<String, SortedMap<Integer, JSONObject>> DOCUMENT_MAP = new HashMap<>();

	/**增删改查统一入口，这个一个方法可替代以下 7 个方法，牺牲一些路由解析性能来提升一点开发效率
	 * @param method
	 * @param tag
	 * @param params
	 * @param request
	 * @param session
	 * @return
	 */
	public String router(String method, String tag, Map<String, String> params, String request, HttpSession session) {
		if (StringUtil.isName(tag) == false) {
			return APIJSONParser.newErrorResult(new IllegalArgumentException("URL 路径 /{method}/{tag} 中 tag 值 " + tag
					+ " 错误！只允许变量命名格式！")).toJSONString();
		}
		if (APIJSON_METHODS.contains(method) == false) {
			return APIJSONParser.newErrorResult(new IllegalArgumentException("URL 路径 /{method}/" + tag + " 中 method 值 " + method
					+ " 错误！只允许 " + APIJSON_METHODS + " 中的一个！")).toJSONString();
		}

		String versionStr = params == null ? null : params.get(APIJSONConstant.VERSION);
		Integer version;
		try {
			version = StringUtil.isEmpty(versionStr, false) ? null : Integer.valueOf(versionStr);
		} 
		catch (Exception e) {
			return APIJSONParser.newErrorResult(new IllegalArgumentException("URL 路径 /" + method
					+ "/" + tag + "?version=value 中 value 值 " + versionStr + " 错误！必须符合整数格式！")).toJSONString();
		}

		if (version == null) {
			version = 0;
		}

		try {
			// 从 Document 查这样的接口		
			String cacheKey = AbstractVerifier.getCacheKeyForRequest(method, tag);
			SortedMap<Integer, JSONObject> versionedMap = DOCUMENT_MAP.get(cacheKey);

			JSONObject result = versionedMap == null ? null : versionedMap.get(version);
			if (result == null) {  // version <= 0 时使用最新，version > 0 时使用 > version 的最接近版本（最小版本）
				Set<Entry<Integer, JSONObject>> set = versionedMap == null ? null : versionedMap.entrySet();

				if (set != null && set.isEmpty() == false) {
					Entry<Integer, JSONObject> maxEntry = null;

					for (Entry<Integer, JSONObject> entry : set) {
						if (entry == null || entry.getKey() == null || entry.getValue() == null) {
							continue;
						}

						if (version == null || version <= 0 || version == entry.getKey()) {  // 这里应该不会出现相等，因为上面 versionedMap.get(Integer.valueOf(version))
							maxEntry = entry;
							break;
						}

						if (entry.getKey() < version) {
							break;
						}

						maxEntry = entry;
					}

					result = maxEntry == null ? null : maxEntry.getValue();
				}

				if (result != null) {  // 加快下次查询，查到值的话组合情况其实是有限的，不属于恶意请求
					if (versionedMap == null) {
						versionedMap = new TreeMap<>((o1, o2) -> {
							return o2 == null ? -1 : o2.compareTo(o1);  // 降序
						});
					}

					versionedMap.put(version, result);
					DOCUMENT_MAP.put(cacheKey, versionedMap);
				}
			}

			APIJSONCreator creator = APIJSONParser.APIJSON_CREATOR;
			if (result == null && Log.DEBUG && DOCUMENT_MAP.isEmpty()) {

				//获取指定的JSON结构 <<<<<<<<<<<<<<
				SQLConfig config = creator.createSQLConfig().setMethod(GET).setTable(APIJSONConstant.DOCUMENT_);
				config.setPrepared(false);
				config.setColumn(Arrays.asList("request,apijson"));

				Map<String, Object> where = new HashMap<String, Object>();
				where.put("url", "/" + method + "/" + tag);
				where.put("apijson{}", "length(apijson)>0");

				if (version > 0) {
					where.put(JSONRequest.KEY_VERSION + ">=", version);
				}
				config.setWhere(where);
				config.setOrder(JSONRequest.KEY_VERSION + (version > 0 ? "+" : "-"));
				config.setCount(1);

				//too many connections error: 不try-catch，可以让客户端看到是服务器内部异常
				result = creator.createSQLExecutor().execute(config, false);

				// version, method, tag 组合情况太多了，JDK 里又没有 LRUCache，所以要么启动时一次性缓存全部后面只用缓存，要么每次都查数据库
				//			versionedMap.put(Integer.valueOf(version), result);
				//			DOCUMENT_MAP.put(cacheKey, versionedMap);
			}

			String apijson = result == null ? null : result.getString("apijson");
			if (StringUtil.isEmpty(apijson, true)) {
				throw new IllegalArgumentException("URL 路径 /" + method
						+ "/" + tag + (versionStr == null ? "" : "?version=" + versionStr) + " 对应的接口不存在！");
			}

			JSONObject rawReq = JSON.parseObject(request);

			RequestMethod requestMethod = RequestMethod.valueOf(method.toUpperCase());
			Parser<Long> parser = newParser(session, requestMethod);

			if (parser.isNeedVerifyContent()) {
				Verifier<Long> verifier = creator.createVerifier();

				//获取指定的JSON结构 <<<<<<<<<<<<
				JSONObject object;
				object = parser.getStructure("Request", method.toUpperCase(), tag, version);
				if (object == null) { //empty表示随意操作  || object.isEmpty()) {
					throw new UnsupportedOperationException("找不到 version: " + version + ", method: " + method.toUpperCase() + ", tag: " + tag + " 对应的 structure ！"
							+ "非开放请求必须是后端 Request 表中校验规则允许的操作！如果需要则在 Request 表中新增配置！");
				}

				JSONObject target = object;

				//JSONObject clone 浅拷贝没用，Structure.parse 会导致 structure 里面被清空，第二次从缓存里取到的就是 {}
				verifier.verifyRequest(requestMethod, "", target, rawReq, 0, null, null, creator);
			}

			JSONObject apijsonReq = JSON.parseObject(apijson);
			if (apijsonReq == null) {
				apijsonReq = new JSONObject(true);
			}

			Set<Entry<String, Object>> rawSet = rawReq == null ? null : rawReq.entrySet();
			if (rawSet != null && rawSet.isEmpty() == false) {
				for (Entry<String, Object> entry : rawSet) {
					String key = entry == null ? null : entry.getKey();
					if (key == null) {  // value 为 null 有效
						continue;
					}

					String[] pathKeys = key.split("\\.");
					//逐层到达child的直接容器JSONObject parent
					int last = pathKeys.length - 1;
					JSONObject parent = apijsonReq;
					for (int i = 0; i < last; i++) {//一步一步到达指定位置
						JSONObject p = parent.getJSONObject(pathKeys[i]);
						if (p == null) {
							p = new JSONObject(true);
							parent.put(key, p);
						}
						parent = p;
					}

					parent.put(pathKeys[last], entry.getValue());
				}
			}

			//没必要，已经是预设好的实际参数了，如果要 tag 就在 apijson 字段配置  apijsonReq.put(JSONRequest.KEY_TAG, tag);

			return parser.setNeedVerifyContent(false).parse(apijsonReq);
		}
		catch (Exception e) {
			return APIJSONParser.newErrorResult(e).toJSONString();
		}
	}

	/**增删改查统一入口，这个一个方法可替代以下 7 个方法，牺牲一些路由解析性能来提升一点开发效率
	 * @param method
	 * @param tag
	 * @param params
	 * @param request
	 * @param session
	 * @return
	 */
	public String rest(String method, String request, HttpSession session) {
		if (APIJSON_METHODS.contains(method)) {
			return parse(RequestMethod.valueOf(method.toUpperCase()), request, session);
		}

		return APIJSONParser.newErrorResult(new IllegalArgumentException("URL 路径 /{method} 中 method 值 " + method
				+ " 错误！只允许 " + APIJSON_METHODS + " 中的一个！")).toJSONString();
	}

	/**获取
	 * @param request 只用String，避免encode后未decode
	 * @param session
	 * @return
	 * @see {@link RequestMethod#GET}
	 */
	public String get(String request, HttpSession session) {
		return parse(GET, request, session);
	}


}
