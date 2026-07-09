// @ts-check
import {themes as prismThemes} from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'BookOrbit',
  tagline: 'Native Android client for self-hosted BookOrbit libraries',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://deranjer.github.io',
  baseUrl: '/bookorbit-android/',

  organizationName: 'deranjer',
  projectName: 'bookorbit-android',

  onBrokenLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/deranjer/bookorbit-android/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/docusaurus-social-card.jpg',
      colorMode: {
        respectPrefersColorScheme: true,
      },
      navbar: {
        title: 'BookOrbit',
        logo: {
          alt: 'BookOrbit Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'docsSidebar',
            position: 'left',
            label: 'Docs',
          },
          {
            href: 'https://deranjer.github.io/bookorbit-android/privacy.html',
            label: 'Privacy Policy',
            position: 'right',
          },
          {
            href: 'https://github.com/deranjer/bookorbit-android',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Getting Started',
                to: '/docs/intro',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Privacy Policy',
                href: 'https://deranjer.github.io/bookorbit-android/privacy.html',
              },
              {
                label: 'GitHub',
                href: 'https://github.com/deranjer/bookorbit-android',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} BookOrbit.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
      },
    }),
};

export default config;
