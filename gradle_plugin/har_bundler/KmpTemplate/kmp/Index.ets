export * from 'libkmp.so'
export function getPromiseResultForKN<T>(promise: Promise<T>, callback: ESObject) {
  promise.then((result: T) => {
    callback['onResult'](result)
  }).catch((err: Error) => {
    let message = ''
    if (typeof err == 'string') {
      message = err
    } else {
      message = err.message
    }
    callback['onError'](message ?? 'No Error Message')
  })
}