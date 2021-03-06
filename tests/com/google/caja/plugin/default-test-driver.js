// Copyright (C) 2010 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * @fileoverview
 * 
 * @requires html4, getUrlParam, basicCajaConfig, URI, readyToTest, jsunitRun,
 *     asyncRequirements, console, createDiv, caja, createExtraImportsForTesting
 */

(function() {
  function getUriEffectName(uriEffect) {
    for (var n in html4.ueffects) {
      if (html4.ueffects[n] === uriEffect) { return n; }
    }
    throw new TypeError('Unknown URI effect ' + uriEffect);
  }

  function getLoaderTypeName(loaderType) {
    for (var n in html4.ltypes) {
      if (html4.ltypes[n] === loaderType) { return n; }
    }
    throw new TypeError('Unknown loader type ' + loaderType);
  }

  var testCase = getUrlParam('test-case');

  if (testCase) {
    caja.makeFrameGroup(basicCajaConfig, function(frameGroup) {
      frameGroup.makeES5Frame(
          createDiv(),
          {
            fetch: function(uri, mime, callback) {
              if (/^http:\/\/caja-test-url.test/.test(uri)) {
                return caja.policy.net.NO_NETWORK.fetch(uri, mime, callback);
              } else {
                return caja.policy.net.ALL.fetch(uri, mime, callback);
              }
            },
            rewrite: function (uri, uriEffect, loaderType, hints) {
              if (uri.getPath().indexOf('test-image-41x13.png') !== -1) {
                // used by test-domado-dom-guest.html
                return 'test-image-41x13.png';
              }
              return URI.create(
                  'http',
                  null,
                  // .test is reserved by RFC 2606
                  'caja-test-url.test',
                  null,
                  '/',
                  [
                    'effect', getUriEffectName(uriEffect),
                    'loader', getLoaderTypeName(loaderType),
                    'uri',    uri
                  ])
                  .toString();
              }
          },
          function(frame) {
            frame.url(testCase)
                 .run(createExtraImportsForTesting(frameGroup, frame),
                     function(result) {
                       readyToTest();
                       jsunitRun(null, asyncRequirements.evaluate);
                     });
          });
    });
  } else {
    console.log('Parameter "test-case" not specified in URL');
  }
})();
