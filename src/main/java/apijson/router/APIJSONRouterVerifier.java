/*Copyright ©2022 APIJSON(https://github.com/APIJSON)

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

import static apijson.JSON.*;
import static apijson.JSONRequest.KEY_COUNT;
import static apijson.framework.APIJSONConstant.DOCUMENT_;
import static apijson.framework.APIJSONConstant.METHODS;

import java.rmi.ServerException;
import java.util.*;

import apijson.JSON;
import apijson.JSONResponse;
import apijson.Log;
import apijson.RequestMethod;
import apijson.StringUtil;
import apijson.framework.APIJSONConstant;
import apijson.framework.APIJSONCreator;
import apijson.framework.APIJSONVerifier;
import apijson.orm.JSONRequest;


/**路由请求映射验证器
 * @author Lemon
 */
public class APIJSONRouterVerifier<T, M extends Map<String, Object>, L extends List<Object>>
		extends APIJSONVerifier<T, M, L> {
	public static final String TAG = "APIJSONRouterVerifier";


	// method-tag, <version, Document>
	public static Map<String, SortedMap<Integer, Map<String, Object>>> DOCUMENT_MAP;
	static {
		DOCUMENT_MAP = new HashMap<>();
	}

	/**初始化，加载所有请求映射配置和请求校验配置
	 * @return 
	 * @throws ServerException
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M init() throws ServerException {
		return init(false);
	}
	/**初始化，加载所有请求映射配置和请求校验配置
	 * @param shutdownWhenServerError 
	 * @return 
	 * @throws ServerException
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M init(boolean shutdownWhenServerError) throws ServerException {
		return init(shutdownWhenServerError, null);
	}
	/**初始化，加载所有请求映射配置和请求校验配置
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M init(APIJSONCreator<T, M, L> creator) throws ServerException {
		return init(false, creator);
	}
	/**初始化，加载所有请求映射配置和请求校验配置
	 * @param shutdownWhenServerError 
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static<T, M extends Map<String, Object>, L extends List<Object>> M init(boolean shutdownWhenServerError
			, APIJSONCreator<T, M, L> creator) throws ServerException {
		M result = APIJSONVerifier.init(shutdownWhenServerError, creator);
		result.put(DOCUMENT_, initDocument(shutdownWhenServerError, creator));
		return result;
	}


	/**初始化，加载所有请求校验配置
	 * @return 
	 * @throws ServerException
	 */
	public static <M extends Map<String, Object>> M initDocument() throws ServerException {
		return initDocument(false);
	}
	/**初始化，加载所有请求校验配置
	 * @param shutdownWhenServerError 
	 * @return 
	 * @throws ServerException
	 */
	public static <M extends Map<String, Object>> M initDocument(boolean shutdownWhenServerError) throws ServerException {
		return initDocument(shutdownWhenServerError, null);
	}
	/**初始化，加载所有请求校验配置
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M initDocument(APIJSONCreator<T, M, L> creator) throws ServerException {
		return initDocument(false, creator);
	}
	/**初始化，加载所有请求校验配置
	 * @param shutdownWhenServerError 
	 * @param creator 
	 * @return 
	 * @throws ServerException
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M initDocument(boolean shutdownWhenServerError, APIJSONCreator<T, M, L> creator) throws ServerException {
		return initDocument(shutdownWhenServerError, creator, null);
	}
	/**初始化，加载所有请求校验配置
	 * @param shutdownWhenServerError 
	 * @param creator 
	 * @param table 表内自定义数据过滤条件
	 * @return 
	 * @throws ServerException
	 */
	@SuppressWarnings("unchecked")
	public static <T, M extends Map<String, Object>, L extends List<Object>> M initDocument(boolean shutdownWhenServerError, APIJSONCreator<T, M, L> creator, M table) throws ServerException {
		if (creator == null) {
			creator = (APIJSONCreator<T, M, L>) APIJSON_CREATOR;
		}
		APIJSON_CREATOR = creator;


		boolean isAll = table == null || table.isEmpty();
		M document = isAll ? JSON.createJSONObject(new JSONRequest().puts("apijson{}", "length(apijson)>0").setOrder("version-,id+")) : table;
		if (Log.DEBUG == false) {
			document.put(APIJSONConstant.KEY_DEBUG, 0);
		}

		M requestItem = JSON.createJSONObject();
		requestItem.put(DOCUMENT_, document);  // 方便查找
		requestItem.put(KEY_COUNT, 0);

		M request = JSON.createJSONObject();
		request.put(DOCUMENT_ + "[]", requestItem);

		M response = creator.createParser().setMethod(RequestMethod.GET).setNeedVerify(false).parseResponse(request);
		if (JSONResponse.isSuccess(response) == false) {
			Log.e(TAG, "\n\n\n\n\n !!!! 查询请求映射配置异常 !!!\n" + getString(response, JSONResponse.KEY_MSG) + "\n\n\n\n\n");
			onServerError("查询请求映射配置异常 !", shutdownWhenServerError);
		}

		L list = getJSONArray(response, DOCUMENT_ + "[]");
		int size = list == null ? 0 : list.size();
		if (isAll && size <= 0) {
			Log.w(TAG, "initDocument isAll && size <= 0，没有可用的请求映射配置");
			return response;
		}

		Log.d(TAG, "initDocument < for DOCUMENT_MAP.size() = " + DOCUMENT_MAP.size() + " <<<<<<<<<<<<<<<<<<<<<<<<");


		Map<String, SortedMap<Integer, Map<String, Object>>> newMap = new LinkedHashMap<>();
		
		for (int i = 0; i < size; i++) {
			M item = getJSONObject(list, i);
			if (item == null) {
				continue;
			}

			String version = getString(item, "version");
			if (StringUtil.isEmpty(version, true)) {
				onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name")
						+ " 对应 version 不能为空！", shutdownWhenServerError);
			}

			String url = getString(item, "url");
			int index = url == null ? -1 : url.indexOf("/");
			if (index != 0) {
				onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
						+ " 对应 url 值错误，必须以 / 开头！", shutdownWhenServerError);
			}

			String requestStr = getString(item, "request");

			String apijson = getString(item, "apijson");
			if (StringUtil.isEmpty(apijson)) {
				if (StringUtil.isBranchUrl(url) == false) {
					onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
							+ " 对应 url 值错误！只允许合法的 URL 格式！", shutdownWhenServerError);			
				}
			}
			else {
				if (StringUtil.isNotEmpty(requestStr)) {
					try {
						JSON.parseObject(requestStr);
					}
					catch (Exception e) {
						onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
								+ " 对应 request 值 " + requestStr + " 错误！只允许合法的 M 格式！" + e.getMessage(), shutdownWhenServerError);			
					}
				}

				try {
					JSON.parseObject(apijson);
				}
				catch (Exception e) {
					onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
							+ " 对应 apijson 值 " + apijson + " 错误！只允许合法的 M 格式！" + e.getMessage(), shutdownWhenServerError);			
				}

				index = url.lastIndexOf("/");
				String method = index < 0 ? null : url.substring(0, index);
				String tag = index < 0 ? null : url.substring(index + 1);

				index = method == null ? -1 : method.lastIndexOf("/");
				method = index < 0 ? method : method.substring(index + 1);
				
				if (METHODS.contains(method) == false) {
					onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
							+ " 对应路径 /{method}/{tag} 中 method 值 " + method + " 错误！apijson 字段不为空时只允许 " + METHODS + " 中的一个！", shutdownWhenServerError);
				}

				if (StringUtil.isName(tag) == false) {
					onServerError("服务器内部错误，Document 表中的 id=" + getString(item, "id") + ", name=" + getString(item, "name") + ", url=" + url
							+ " 对应路径 /{method}/{tag} 中 tag 值 " + tag + " 错误！apijson 字段不为空时只允许变量命名格式！", shutdownWhenServerError);			
				}

				String cacheKey = getCacheKeyForRequest(method, tag);
				SortedMap<Integer, Map<String, Object>> versionedMap = newMap.get(cacheKey);
				if (versionedMap == null) {
					versionedMap = new TreeMap<>(new Comparator<Integer>() {

						@Override
						public int compare(Integer o1, Integer o2) {
							return o2 == null ? -1 : o2.compareTo(o1);  // 降序
						}
					});
				}
				versionedMap.put(Integer.valueOf(version), item);
				newMap.put(cacheKey, versionedMap);
			}

		}

		if (isAll) {  // 全量更新
			DOCUMENT_MAP = newMap;
		}
		else {
			DOCUMENT_MAP.putAll(newMap);
		}
		
		Log.d(TAG, "initDocument  for /> DOCUMENT_MAP.size() = " + DOCUMENT_MAP.size() + " >>>>>>>>>>>>>>>>>>>>>>>");

		return response;
	}


	protected static void onServerError(String msg, boolean shutdown) throws ServerException {
		Log.e(TAG, "\n接口映射配置测试未通过！\n请修改 Document 表里的记录！\n保证前端看到的接口映射配置文档是正确的并且能正常使用！！！\n\n原因：\n" + msg);

		if (shutdown) {
			System.exit(1);	
		} else {
			throw new ServerException(msg);
		}
	}


}
