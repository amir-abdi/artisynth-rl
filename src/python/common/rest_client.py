import json
import logging

import requests

from common import constants as c

logger = logging.getLogger()


class RestClient:
    def __init__(self, ip, port):
        self.ip = ip
        self.port = port
        self.server_is_alive(ip, port)

    @staticmethod
    def get_url(ip, port, message):
        return 'http://{}:{}/{}'.format(ip, port, message)

    @staticmethod
    def server_is_alive(ip, port):
        url = RestClient.get_url(ip, port, '')
        try:
            response = requests.get(url)
        except requests.exceptions.ConnectionError:
            logger.log(msg='ArtiSynth server is not live at {}'.format(url), level=logging.INFO)
            return False
        if response.ok:
            return True
        else:
            logger.log(msg='ArtiSynth server at {} response={}'.format(url, response.content),
                       level=logging.INFO)

    def get_post(self, obj=None, request_type=c.GET_STR, message=''):
        if not obj:
            obj = dict()

        try:
            url = RestClient.get_url(self.ip, self.port, message)
            r_content = dict()
            if request_type == c.GET_STR:
                response = requests.get(url)
                if response.ok:
                    r_content = json.loads(response.content.decode())
                    logger.debug(r_content)
            elif request_type == c.POST_STR:
                response = requests.post(url, json=obj)

                if response.ok:
                    r_content = json.loads(response.content.decode())

            logger.debug('obj sent size: {}'.format(obj))
            logger.debug('obj sent: ' + str(obj))
            logger.debug('receive content', r_content)

            return r_content

        except NameError as err:
            logger.exception('NameError in send: {}'.format(err))
            raise err
        except BrokenPipeError as err:
            logger.exception('BrokenPipeError in send: {}'.format(err))
